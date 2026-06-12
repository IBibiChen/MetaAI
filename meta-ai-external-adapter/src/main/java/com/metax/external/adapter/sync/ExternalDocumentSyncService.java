package com.metax.external.adapter.sync;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.metax.external.adapter.config.ExternalAdapterProperties;
import com.metax.external.adapter.source.ExternalFileDownloadClient;
import com.metax.external.adapter.source.ExternalSourceFileService;
import com.metax.external.adapter.source.ExternalSourceFileDO;
import com.metax.external.adapter.source.ExternalDownloadedFile;
import com.metax.external.adapter.storage.ExternalStorageDocumentClient;
import com.metax.external.adapter.storage.ExternalStorageUploadResult;
import com.metax.external.adapter.storage.ExternalStorageDocumentDO;
import com.metax.external.adapter.storage.ExternalStorageDocumentMapper;
import com.metax.external.adapter.sync.model.ExternalDocumentLearningStatus;
import com.metax.external.adapter.sync.model.ExternalDocumentSyncStatus;
import com.metax.external.adapter.web.ExternalDocumentSyncResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * ExternalDocumentSyncService .
 *
 * <p>
 * 第三方系统文件同步编排服务
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/11
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "metax.external-adapter.enabled", havingValue = "true")
public class ExternalDocumentSyncService extends ServiceImpl<ExternalDocumentSyncMapper, ExternalDocumentSyncDO> {

    private final ExternalSourceFileService sourceFileService;

    private final ExternalFileDownloadClient fileDownloadClient;

    private final ExternalStorageDocumentClient storageDocumentClient;

    private final ExternalAdapterProperties properties;

    private final ExternalStorageDocumentMapper storageDocumentMapper;

    /**
     * 接收第三方系统文件学习通知
     *
     * <p>
     * 该方法只落定同步任务和源表学习中状态，耗时下载、上传和索引提交交给后台任务
     *
     * @param externalFileId 第三方系统文件 ID
     * @return 同步响应
     */
    @Transactional(rollbackFor = Exception.class)
    public ExternalDocumentSyncResponse requestSync(String externalFileId) {

        ExternalSourceFileDO file = sourceFileService.findLearnableById(externalFileId)
                .orElseThrow(() -> new IllegalArgumentException("第三方系统文档不存在或不需要学习：" + externalFileId));

        ExternalDocumentSyncDO sync = upsertPending(file);
        sourceFileService.updateLearningStatus(file.getId(), ExternalDocumentLearningStatus.LEARNING, sync.getDocumentId());
        log.info("第三方系统文档学习已入队：externalFileId = {}，syncStatus = {}，documentId = {}",
                sync.getExternalFileId(), sync.getSyncStatus(), sync.getDocumentId());

        return response(sync, ExternalDocumentLearningStatus.LEARNING);
    }

    /**
     * 拉取并处理一条队列任务
     *
     * <p>
     * Worker 每轮只调用该方法一次
     * 如果成功抢占到任务，本方法会一直阻塞到该任务完成、失败、进入重试等待或索引等待超时
     * 因此 Worker 外层不需要再额外加本机内存锁
     *
     * @return true 表示本轮处理过任务
     */
    public boolean pollAndProcessOneTask() {
        Optional<ExternalDocumentSyncDO> optionalSync = claimNextExecutableTask();
        if (optionalSync.isEmpty()) {
            return false;
        }
        ExternalDocumentSyncDO sync = optionalSync.get();
        log.info("第三方系统文档同步任务已抢占：externalFileId = {}，syncStatus = {}，attemptCount = {}",
                sync.getExternalFileId(), sync.getSyncStatus(), sync.getAttemptCount());
        processClaimedTask(sync);
        return true;
    }

    /**
     * 执行已抢占的同步任务
     *
     * <p>
     * 这里是单个文件的完整闭环：
     * 先确认外部源文件仍然存在并且需要学习，再下载文件、写入对象存储、提交索引，最后等待索引终态
     * 只有该方法返回后，Worker 才会继续拉取下一条任务
     *
     * @param sync 已锁定的同步记录
     */
    private void processClaimedTask(ExternalDocumentSyncDO sync) {
        // 抢占到任务后先回查第三方源表，确保文件仍然存在且仍然带有学习标记
        Optional<ExternalSourceFileDO> optionalFile = sourceFileService.findLearnableById(sync.getExternalFileId());
        if (optionalFile.isEmpty()) {
            // 源文件不存在或不再需要学习时，不能继续下载和索引，只能进入重试或失败状态
            markRetryWaitingOrFailed(sync, "第三方系统文档不存在或不需要学习");
            return;
        }
        ExternalSourceFileDO file = optionalFile.get();
        try {
            if (StringUtils.hasText(sync.getDocumentId()) && ExternalDocumentSyncStatus.INDEXED.name().equals(sync.getSyncStatus())) {
                // 已有 documentId 且内部状态已完成，说明这是幂等重复触发，直接返回
                return;
            }
            if (!StringUtils.hasText(sync.getDocumentId())) {
                // 没有 documentId 说明文件尚未进入本系统对象存储，需要完整执行下载、上传和索引提交
                sync = downloadStoreAndSubmitIndex(sync, file);
            } else if (!ExternalDocumentSyncStatus.INDEXING.name().equals(sync.getSyncStatus())) {
                // 已有 documentId 说明文件已经归档，重试时只重新提交索引，避免重复上传同一份大文件
                resubmitIndex(sync);
            }
            // 无论是首次提交还是重新提交，最后都要等待对象存储文档进入 INDEXED / INDEX_FAILED 终态
            waitUntilIndexingFinished(sync);
        } catch (WorkerInterruptedException ex) {
            // Worker 停止不是文件业务失败，释放当前任务，等待应用重启后重新接管
            releaseClaimForShutdown(sync, ex.getMessage());
            log.info("第三方系统文档同步因 Worker 停止而释放：externalFileId = {}，documentId = {}",
                    sync.getExternalFileId(), sync.getDocumentId());
        } catch (RuntimeException ex) {
            // 运行时异常按任务失败处理，重新读取最新同步记录，避免用旧 attemptCount 判断重试次数
            markRetryWaitingOrFailed(requireSync(file.getId()), ex.getMessage());
            log.warn("第三方系统文档同步失败：externalFileId = {}，error = {}", file.getId(), ex.getMessage(), ex);
        }
    }

    /**
     * 下载源文件、保存到对象存储并提交索引
     *
     * <p>
     * 只有同步记录还没有 documentId 时才会走这一步
     * 一旦对象存储已经生成 documentId，后续重试只重新提交索引，避免重复上传同一份大文件
     *
     * @param sync 同步记录
     * @param file 第三方系统文件快照
     * @return 已进入索引中的同步记录
     */
    private ExternalDocumentSyncDO downloadStoreAndSubmitIndex(ExternalDocumentSyncDO sync, ExternalSourceFileDO file) {
        markRunning(sync, ExternalDocumentSyncStatus.DOWNLOADING);
        ExternalDownloadedFile downloadedFile = fileDownloadClient.download(file);
        markRunning(sync, ExternalDocumentSyncStatus.STORED);
        ExternalStorageUploadResult upload = storageDocumentClient.upload(downloadedFile);
        return markIndexingSubmitted(sync, file, upload);
    }

    /**
     * 同步已提交索引的学习结果
     *
     * <p>
     * 为了保持 agent / rag 0 入侵，这里通过查询 meta_storage_document 状态完成源表回写
     *
     * @param limit 最大处理数量
     */
    public void reconcileIndexStatus(int limit) {
        List<ExternalDocumentSyncDO> records = list(new LambdaQueryWrapper<ExternalDocumentSyncDO>()
                .eq(ExternalDocumentSyncDO::getSyncStatus, ExternalDocumentSyncStatus.INDEXING.name())
                .isNotNull(ExternalDocumentSyncDO::getDocumentId)
                .last("limit " + limit));
        for (ExternalDocumentSyncDO record : records) {
            String indexStatus = findStorageIndexStatus(record.getDocumentId());
            if ("INDEXED".equals(indexStatus)) {
                markCompleted(record);
            } else if ("INDEX_FAILED".equals(indexStatus)) {
                markRetryWaitingOrFailed(record, "对象存储文档索引失败");
            }
        }
    }

    /**
     * 抢占下一条可执行任务
     *
     * <p>
     * 抢占 SQL 放在 ExternalDocumentSyncMapper 中，使用 PostgreSQL 的 FOR UPDATE SKIP LOCKED 和 UPDATE RETURNING
     * 这里仅负责计算时间窗口和 Worker 标识，保持业务编排代码可读
     *
     * <p>
     * 不能只 select 后直接 updateById，否则多个 Worker 或应用重启并发时可能重复处理同一文件
     *
     * @return 已锁定的同步记录
     */
    private Optional<ExternalDocumentSyncDO> claimNextExecutableTask() {
        Instant now = Instant.now();
        Instant expiredAt = now.minus(properties.getWorker().getLockTimeout());
        return Optional.ofNullable(baseMapper.claimNextExecutableTask(now, expiredAt, properties.getMaxAttempts(), workerId()));
    }

    /**
     * 创建或更新待同步记录
     *
     * @param file 第三方系统文件快照
     * @return 同步记录
     */
    private ExternalDocumentSyncDO upsertPending(ExternalSourceFileDO file) {
        // externalFileId 在同步表中有唯一约束，先按第三方文件 ID 查询，保证重复通知是幂等更新
        ExternalDocumentSyncDO existing = getOne(new LambdaQueryWrapper<ExternalDocumentSyncDO>()
                .eq(ExternalDocumentSyncDO::getExternalFileId, file.getId()));
        Instant now = Instant.now();
        if (existing != null) {
            // 既有记录要先判断文件内容或状态是否需要重新学习，不能所有重复通知都重置队列
            boolean requeue = shouldRequeue(existing, file);
            if (requeue) {
                // 文件首次失败、等待重试或 hash 变化时，重置任务状态并重新放回待处理队列
                return resetForRequeue(existing, file, now);
            }
            // 不需要重新学习时，只刷新第三方源文件快照，保持当前同步状态和 documentId 不变
            return refreshSourceSnapshot(existing, file, now);
        }
        // 没有同步记录说明这是第一次通知或历史补偿扫描发现的新文件，默认创建 PENDING 任务
        ExternalDocumentSyncDO entity = ExternalDocumentSyncDO.builder()
                .externalFileId(file.getId())
                .externalFilePath(file.getFilePath())
                .externalHashCode(file.getHashCode())
                .tenantId(properties.getTenantId())
                .kbId(properties.getKbId())
                .syncStatus(ExternalDocumentSyncStatus.PENDING.name())
                .attemptCount(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        // 新任务只入持久队列，不在接口线程里执行下载、OCR、ETL 和向量化
        save(entity);
        return entity;
    }

    /**
     * 重置既有同步记录并重新入队
     *
     * <p>
     * MyBatis Plus updateById 默认可能跳过 null 字段
     * 这里必须使用 LambdaUpdateWrapper 显式 set null，确保旧 documentId、锁信息和完成时间真正被清空
     *
     * @param existing 既有同步记录
     * @param file     第三方系统文件快照
     * @param now      当前时间
     * @return 重置后的同步记录
     */
    private ExternalDocumentSyncDO resetForRequeue(ExternalDocumentSyncDO existing, ExternalSourceFileDO file, Instant now) {
        update(new LambdaUpdateWrapper<ExternalDocumentSyncDO>()
                .eq(ExternalDocumentSyncDO::getId, existing.getId())
                .set(ExternalDocumentSyncDO::getExternalFilePath, file.getFilePath())
                .set(ExternalDocumentSyncDO::getExternalHashCode, file.getHashCode())
                .set(ExternalDocumentSyncDO::getTenantId, properties.getTenantId())
                .set(ExternalDocumentSyncDO::getKbId, properties.getKbId())
                .set(ExternalDocumentSyncDO::getSyncStatus, ExternalDocumentSyncStatus.PENDING.name())
                .set(ExternalDocumentSyncDO::getDocumentId, null)
                .set(ExternalDocumentSyncDO::getAttemptCount, 0)
                .set(ExternalDocumentSyncDO::getLastError, null)
                .set(ExternalDocumentSyncDO::getFinishedAt, null)
                .set(ExternalDocumentSyncDO::getNextAttemptAt, null)
                .set(ExternalDocumentSyncDO::getLockedBy, null)
                .set(ExternalDocumentSyncDO::getLockedAt, null)
                .set(ExternalDocumentSyncDO::getUpdatedAt, now));
        return requireSync(file.getId());
    }

    /**
     * 刷新既有同步记录的第三方源文件快照
     *
     * <p>
     * 文件不需要重新学习时，只同步路径、hash、租户和知识库等快照字段
     * 不触碰 documentId、syncStatus 和锁字段，避免影响正在执行或已完成的任务
     *
     * @param existing 既有同步记录
     * @param file     第三方系统文件快照
     * @param now      当前时间
     * @return 刷新后的同步记录
     */
    private ExternalDocumentSyncDO refreshSourceSnapshot(ExternalDocumentSyncDO existing,
                                                         ExternalSourceFileDO file,
                                                         Instant now) {
        update(new LambdaUpdateWrapper<ExternalDocumentSyncDO>()
                .eq(ExternalDocumentSyncDO::getId, existing.getId())
                .set(ExternalDocumentSyncDO::getExternalFilePath, file.getFilePath())
                .set(ExternalDocumentSyncDO::getExternalHashCode, file.getHashCode())
                .set(ExternalDocumentSyncDO::getTenantId, properties.getTenantId())
                .set(ExternalDocumentSyncDO::getKbId, properties.getKbId())
                .set(ExternalDocumentSyncDO::getLastError, null)
                .set(ExternalDocumentSyncDO::getUpdatedAt, now));
        return requireSync(file.getId());
    }

    /**
     * 判断既有同步记录是否需要重新入队
     *
     * <p>
     * 已完成且文件 hash 未变化时保持幂等返回，避免重复向量化
     * 运行中的任务只更新源文件快照，不抢占当前处理过程
     *
     * @param existing 既有同步记录
     * @param file     第三方系统文件快照
     * @return true 表示需要重新进入待处理队列
     */
    private boolean shouldRequeue(ExternalDocumentSyncDO existing, ExternalSourceFileDO file) {
        if (ExternalDocumentSyncStatus.INDEXING.name().equals(existing.getSyncStatus())
                || ExternalDocumentSyncStatus.DOWNLOADING.name().equals(existing.getSyncStatus())
                || ExternalDocumentSyncStatus.STORED.name().equals(existing.getSyncStatus())) {
            return false;
        }
        if (ExternalDocumentSyncStatus.INDEXED.name().equals(existing.getSyncStatus())) {
            return !StringUtils.hasText(existing.getExternalHashCode())
                    || !existing.getExternalHashCode().equals(file.getHashCode());
        }
        return true;
    }

    /**
     * 查询同步记录
     *
     * @param externalFileId 第三方系统文件 ID
     * @return 同步记录
     */
    private ExternalDocumentSyncDO requireSync(String externalFileId) {
        ExternalDocumentSyncDO sync = getOne(new LambdaQueryWrapper<ExternalDocumentSyncDO>()
                .eq(ExternalDocumentSyncDO::getExternalFileId, externalFileId));
        if (sync == null) {
            throw new IllegalStateException("第三方系统同步记录不存在：" + externalFileId);
        }
        return sync;
    }

    /**
     * 标记运行中状态
     *
     * @param sync   同步记录
     * @param status 运行中状态
     */
    private void markRunning(ExternalDocumentSyncDO sync, ExternalDocumentSyncStatus status) {
        update(new LambdaUpdateWrapper<ExternalDocumentSyncDO>()
                .eq(ExternalDocumentSyncDO::getId, sync.getId())
                .set(ExternalDocumentSyncDO::getSyncStatus, status.name())
                .set(ExternalDocumentSyncDO::getUpdatedAt, Instant.now()));
    }

    /**
     * 标记索引已提交
     *
     * <p>
     * 对象存储上传接口返回 documentId 后，说明文件元数据已经进入本系统
     * 但 OCR / ETL / 向量化是异步完成的，所以同步状态只能先进入 INDEXING
     *
     * @param sync   同步记录
     * @param file   第三方系统文件快照
     * @param upload 上传结果
     */
    private ExternalDocumentSyncDO markIndexingSubmitted(ExternalDocumentSyncDO sync,
                                                         ExternalSourceFileDO file,
                                                         ExternalStorageUploadResult upload) {
        update(new LambdaUpdateWrapper<ExternalDocumentSyncDO>()
                .eq(ExternalDocumentSyncDO::getId, sync.getId())
                .set(ExternalDocumentSyncDO::getDocumentId, upload.documentId())
                .set(ExternalDocumentSyncDO::getSyncStatus, ExternalDocumentSyncStatus.INDEXING.name())
                .set(ExternalDocumentSyncDO::getLastSyncedAt, Instant.now())
                .set(ExternalDocumentSyncDO::getUpdatedAt, Instant.now()));
        sourceFileService.updateLearningStatus(file.getId(), ExternalDocumentLearningStatus.LEARNING, upload.documentId());
        log.info("第三方系统文档已提交学习：externalFileId = {}，documentId = {}，indexStatus = {}",
                file.getId(), upload.documentId(), upload.indexStatus());
        return requireSync(file.getId());
    }

    /**
     * 重新提交已归档文档索引
     *
     * <p>
     * 下载和对象存储上传已成功时，后续重试只需要重新提交索引
     * 避免重复写对象存储产生多个业务不可见副本
     *
     * @param sync 同步记录
     */
    private void resubmitIndex(ExternalDocumentSyncDO sync) {
        storageDocumentClient.index(sync.getDocumentId());
        update(new LambdaUpdateWrapper<ExternalDocumentSyncDO>()
                .eq(ExternalDocumentSyncDO::getId, sync.getId())
                .set(ExternalDocumentSyncDO::getSyncStatus, ExternalDocumentSyncStatus.INDEXING.name())
                .set(ExternalDocumentSyncDO::getUpdatedAt, Instant.now()));
        log.info("第三方系统文档重新提交学习：externalFileId = {}，documentId = {}",
                sync.getExternalFileId(), sync.getDocumentId());
    }

    /**
     * 等待当前文件索引进入终态
     *
     * <p>
     * 对象存储上传接口会异步提交 ETL，因此这里必须等待 INDEXED / INDEX_FAILED
     * 轮询采用指数退避，避免后台 OCR / ETL 长时间运行时高频查库
     *
     * <p>
     * 该方法是串行 Worker 的关键控制点
     * 只有当前文件进入终态或超过 indexTimeout，Worker 才会释放通道处理下一个文件
     *
     * @param sync 同步记录
     */
    private void waitUntilIndexingFinished(ExternalDocumentSyncDO sync) {
        Instant deadline = Instant.now().plus(properties.getWorker().getIndexTimeout());
        IndexPollingBackoff backoff = new IndexPollingBackoff(properties.getWorker().getIndexPollInitialInterval(),
                properties.getWorker().getIndexPollMaxInterval(), properties.getWorker().getIndexPollMultiplier());
        log.info("第三方系统文档进入索引等待：externalFileId = {}，documentId = {}，indexTimeout = {}",
                sync.getExternalFileId(), sync.getDocumentId(), properties.getWorker().getIndexTimeout());
        while (Instant.now().isBefore(deadline)) {
            String indexStatus = findStorageIndexStatus(sync.getDocumentId());
            if ("INDEXED".equals(indexStatus)) {
                markCompleted(sync);
                return;
            }
            if ("INDEX_FAILED".equals(indexStatus)) {
                markRetryWaitingOrFailed(sync, "对象存储文档索引失败");
                return;
            }
            sleep(backoff.nextInterval());
        }
        markRetryWaitingOrFailed(sync, "对象存储文档索引等待超时");
    }

    /**
     * 标记学习完成
     *
     * @param record 同步记录
     */
    private void markCompleted(ExternalDocumentSyncDO record) {
        update(new LambdaUpdateWrapper<ExternalDocumentSyncDO>()
                .eq(ExternalDocumentSyncDO::getId, record.getId())
                .set(ExternalDocumentSyncDO::getSyncStatus, ExternalDocumentSyncStatus.INDEXED.name())
                .set(ExternalDocumentSyncDO::getLastError, null)
                .set(ExternalDocumentSyncDO::getLastSyncedAt, Instant.now())
                .set(ExternalDocumentSyncDO::getFinishedAt, Instant.now())
                .set(ExternalDocumentSyncDO::getLockedBy, null)
                .set(ExternalDocumentSyncDO::getLockedAt, null)
                .set(ExternalDocumentSyncDO::getUpdatedAt, Instant.now()));
        sourceFileService.updateLearningStatus(record.getExternalFileId(), ExternalDocumentLearningStatus.COMPLETED,
                record.getDocumentId());
        log.info("第三方系统文档学习完成：externalFileId = {}，documentId = {}",
                record.getExternalFileId(), record.getDocumentId());
    }

    /**
     * 标记重试等待或终态失败
     *
     * <p>
     * 未达到最大重试次数时释放锁并进入 RETRY_WAIT
     * 达到最大重试次数时进入 FAILED，同时回写第三方系统学习失败状态
     *
     * @param sync         同步记录
     * @param errorMessage 错误信息
     */
    private void markRetryWaitingOrFailed(ExternalDocumentSyncDO sync, String errorMessage) {
        String message = StringUtils.hasText(errorMessage) ? errorMessage : "未知错误";
        if (sync.getAttemptCount() != null && sync.getAttemptCount() >= properties.getMaxAttempts()) {
            markFailed(sync, message);
            return;
        }
        Instant nextAttemptAt = Instant.now().plus(retryDelay(sync));
        update(new LambdaUpdateWrapper<ExternalDocumentSyncDO>()
                .eq(ExternalDocumentSyncDO::getId, sync.getId())
                .set(ExternalDocumentSyncDO::getSyncStatus, ExternalDocumentSyncStatus.RETRY_WAIT.name())
                .set(ExternalDocumentSyncDO::getLastError, truncate(message))
                .set(ExternalDocumentSyncDO::getNextAttemptAt, nextAttemptAt)
                .set(ExternalDocumentSyncDO::getLockedBy, null)
                .set(ExternalDocumentSyncDO::getLockedAt, null)
                .set(ExternalDocumentSyncDO::getUpdatedAt, Instant.now()));
        sourceFileService.updateLearningStatus(sync.getExternalFileId(), ExternalDocumentLearningStatus.LEARNING,
                sync.getDocumentId());
        log.warn("第三方系统文档学习等待重试：externalFileId = {}，attemptCount = {}，nextAttemptAt = {}，error = {}",
                sync.getExternalFileId(), sync.getAttemptCount(), nextAttemptAt, message);
    }

    /**
     * 标记终态失败
     *
     * @param sync         同步记录
     * @param errorMessage 错误信息
     */
    private void markFailed(ExternalDocumentSyncDO sync, String errorMessage) {
        update(new LambdaUpdateWrapper<ExternalDocumentSyncDO>()
                .eq(ExternalDocumentSyncDO::getId, sync.getId())
                .set(ExternalDocumentSyncDO::getSyncStatus, ExternalDocumentSyncStatus.FAILED.name())
                .set(ExternalDocumentSyncDO::getLastError, truncate(errorMessage))
                .set(ExternalDocumentSyncDO::getFinishedAt, Instant.now())
                .set(ExternalDocumentSyncDO::getLockedBy, null)
                .set(ExternalDocumentSyncDO::getLockedAt, null)
                .set(ExternalDocumentSyncDO::getUpdatedAt, Instant.now()));
        sourceFileService.updateLearningStatus(sync.getExternalFileId(), ExternalDocumentLearningStatus.FAILED,
                sync.getDocumentId());
        log.warn("第三方系统文档学习终态失败：externalFileId = {}，documentId = {}，error = {}",
                sync.getExternalFileId(), sync.getDocumentId(), errorMessage);
    }

    /**
     * 停机时释放当前任务并允许重启后继续处理
     *
     * <p>
     * Worker 停止不是文件业务失败，不能把第三方系统状态回写为失败
     * 这里把任务放回 RETRY_WAIT 并设置 nextAttemptAt = now，让下次启动后可以立即接管
     *
     * @param sync         同步记录
     * @param errorMessage 中断原因
     */
    private void releaseClaimForShutdown(ExternalDocumentSyncDO sync, String errorMessage) {
        Instant now = Instant.now();
        update(new LambdaUpdateWrapper<ExternalDocumentSyncDO>()
                .eq(ExternalDocumentSyncDO::getId, sync.getId())
                .set(ExternalDocumentSyncDO::getSyncStatus, ExternalDocumentSyncStatus.RETRY_WAIT.name())
                .set(ExternalDocumentSyncDO::getLastError, truncate(errorMessage))
                .set(ExternalDocumentSyncDO::getNextAttemptAt, now)
                .set(ExternalDocumentSyncDO::getLockedBy, null)
                .set(ExternalDocumentSyncDO::getLockedAt, null)
                .set(ExternalDocumentSyncDO::getUpdatedAt, now));
    }

    /**
     * 计算下一次重试延迟
     *
     * @param sync 同步记录
     * @return 重试延迟
     */
    private Duration retryDelay(ExternalDocumentSyncDO sync) {
        int attemptCount = Optional.ofNullable(sync.getAttemptCount()).orElse(1);
        return switch (attemptCount) {
            case 0, 1 -> Duration.ofMinutes(1);
            case 2 -> Duration.ofMinutes(5);
            default -> Duration.ofMinutes(15);
        };
    }

    /**
     * 查询对象存储文档索引状态
     *
     * <p>
     * 适配器不直接侵入 agent / rag 模块，只通过 meta_storage_document 的 indexStatus 观察索引终态
     *
     * @param documentId 本系统文档 ID
     * @return 索引状态
     */
    private String findStorageIndexStatus(String documentId) {
        ExternalStorageDocumentDO document = storageDocumentMapper.selectOne(
                new LambdaQueryWrapper<ExternalStorageDocumentDO>()
                        .eq(ExternalStorageDocumentDO::getDocumentId, documentId)
                        .eq(ExternalStorageDocumentDO::getDeleted, Boolean.FALSE)
                        .last("limit 1"));
        return document == null ? null : document.getIndexStatus();
    }

    /**
     * 截断错误信息
     *
     * @param message 错误信息
     * @return 截断后的错误信息
     */
    private String truncate(String message) {
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    /**
     * 等待下一次状态轮询
     *
     * @param duration 等待时长
     */
    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new WorkerInterruptedException("第三方系统同步 Worker 被中断", ex);
        }
    }

    /**
     * 当前 worker 标识
     *
     * @return worker 标识
     */
    private String workerId() {
        return "external-adapter-" + Integer.toHexString(System.identityHashCode(this));
    }

    /**
     * 构造同步响应
     *
     * @param sync           同步记录
     * @param externalStatus 第三方系统学习状态
     * @return 同步响应
     */
    private ExternalDocumentSyncResponse response(ExternalDocumentSyncDO sync, ExternalDocumentLearningStatus externalStatus) {
        return new ExternalDocumentSyncResponse(sync.getExternalFileId(), sync.getDocumentId(), sync.getSyncStatus(),
                externalStatus.code());
    }

    /**
     * Worker 中断异常
     *
     * <p>
     * 用于区分应用停机中断和真实文件处理失败
     */
    private static class WorkerInterruptedException extends RuntimeException {

        private WorkerInterruptedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
