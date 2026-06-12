package com.metax.external.adapter.source;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.metax.external.adapter.sync.model.ExternalDocumentLearningStatus;
import com.metax.external.adapter.sync.model.ExternalDocumentSyncStatus;
import com.metax.external.adapter.sync.ExternalDocumentSyncDO;
import com.metax.external.adapter.sync.ExternalDocumentSyncMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ExternalSourceFileService .
 *
 * <p>
 * 第三方系统资料库文件访问服务
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/12
 */
@Service
@ConditionalOnProperty(name = "metax.external-adapter.enabled", havingValue = "true")
public class ExternalSourceFileService extends ServiceImpl<ExternalSourceFileMapper, ExternalSourceFileDO> {

    /**
     * 兜底补偿单次最多扫描页数
     *
     * <p>
     * 防止历史文件量很大时，一次定时任务把第三方源表全量扫穿
     * 真正的重型处理仍由 Worker 串行消费，补偿任务只负责温和地把漏通知文件入队
     */
    private static final int MAX_RECONCILE_SCAN_PAGES = 20;

    /**
     * 同步队列表 Mapper
     *
     * <p>
     * 这里不通过 Service 自调用查询同步表，是为了在源文件扫描时一次性拿到同步快照
     * 避免每个文件单独查一次同步记录
     */
    private final ExternalDocumentSyncMapper syncMapper;

    /**
     * 创建第三方源文件访问服务
     *
     * @param syncMapper 同步队列表 Mapper
     */
    public ExternalSourceFileService(ExternalDocumentSyncMapper syncMapper) {
        this.syncMapper = syncMapper;
    }

    /**
     * 按文件 ID 查询需要学习的第三方系统文件
     *
     * @param externalFileId 第三方系统文件 ID
     * @return 第三方系统文件
     */
    public Optional<ExternalSourceFileDO> findLearnableById(String externalFileId) {
        // 只允许 libraryType 有值且未删除的文件进入学习链路，避免普通资料文件被误同步
        ExternalSourceFileDO file = getOne(learnableQuery()
                .eq(ExternalSourceFileDO::getId, externalFileId));
        return Optional.ofNullable(file);
    }

    /**
     * 查询需要兜底补偿的第三方系统文件
     *
     * <p>
     * 使用分页扫描配合同步表快照过滤，避免动态表名和手写 SQL
     *
     * @param limit       最大记录数
     * @param maxAttempts 最大重试次数
     * @return 第三方系统文件列表
     */
    public List<ExternalSourceFileDO> findLearnableForReconcile(int limit, int maxAttempts) {
        List<ExternalSourceFileDO> candidates = new ArrayList<>();
        long pageNo = 1L;
        while (candidates.size() < limit && pageNo <= MAX_RECONCILE_SCAN_PAGES) {
            // 分页扫描第三方源表，按更新时间倒序优先补偿最近上传或最近变更的文件
            Page<ExternalSourceFileDO> page = page(Page.of(pageNo, limit), learnableQuery()
                    .orderByDesc(ExternalSourceFileDO::getUpdateTime)
                    .orderByDesc(ExternalSourceFileDO::getId));
            List<ExternalSourceFileDO> records = page.getRecords();
            if (CollectionUtils.isEmpty(records)) {
                break;
            }
            // 同一页文件先批量查询同步表快照，再在内存中判断是否需要入队
            candidates.addAll(filterReconcileCandidates(records, maxAttempts, limit - candidates.size()));
            if (!page.hasNext()) {
                break;
            }
            pageNo++;
        }
        return candidates;
    }

    /**
     * 回写第三方系统 AI 文件学习状态
     *
     * @param externalFileId 第三方系统文件 ID
     * @param status         第三方系统学习状态
     * @param documentId     本系统文档 ID
     */
    public void updateLearningStatus(String externalFileId, ExternalDocumentLearningStatus status, String documentId) {
        // 第三方系统只识别整数状态码，这里统一通过枚举映射，避免业务代码散落魔法数字
        LambdaUpdateWrapper<ExternalSourceFileDO> updateWrapper = new LambdaUpdateWrapper<ExternalSourceFileDO>()
                .eq(ExternalSourceFileDO::getId, externalFileId)
                .set(ExternalSourceFileDO::getStatus, status.code())
                .set(ExternalSourceFileDO::getUpdateTime, Instant.now());
        if (StringUtils.hasText(documentId)) {
            // documentId 只有在本系统已经生成文档记录时才回写，避免空值覆盖第三方表中的既有关联
            updateWrapper.set(ExternalSourceFileDO::getChatFileId, documentId);
        }
        update(updateWrapper);
    }

    /**
     * 过滤本轮需要补偿入队的源文件
     *
     * @param files       当前分页文件列表
     * @param maxAttempts 最大重试次数
     * @param remaining   剩余需要数量
     * @return 需要补偿入队的文件列表
     */
    private List<ExternalSourceFileDO> filterReconcileCandidates(List<ExternalSourceFileDO> files,
                                                                 int maxAttempts,
                                                                 int remaining) {
        // 先拿到当前分页文件对应的同步记录，后续判断只读内存快照，减少数据库往返
        Map<String, ExternalDocumentSyncDO> syncMap = syncMap(files);
        return files.stream()
                .filter(file -> shouldReconcile(file, syncMap.get(file.getId()), maxAttempts))
                .limit(remaining)
                .toList();
    }

    /**
     * 判断源文件是否需要兜底入队
     *
     * @param file        第三方系统文件
     * @param sync        同步记录
     * @param maxAttempts 最大重试次数
     * @return true 表示需要补偿入队
     */
    private boolean shouldReconcile(ExternalSourceFileDO file, ExternalDocumentSyncDO sync, int maxAttempts) {
        if (sync == null) {
            // 第三方文件存在但适配器同步表没有记录，通常是历史数据或上传通知漏调，需要补偿入队
            return true;
        }
        if (!StringUtils.hasText(sync.getExternalHashCode())) {
            // 旧同步记录缺少 hash 快照，无法证明文件未变化，保守重新入队
            return true;
        }
        if (!sync.getExternalHashCode().equals(file.getHashCode())) {
            // 第三方文件内容发生变化，需要重新下载、归档并提交索引
            return true;
        }
        // 等待重试或终态失败的记录，只要还没超过最大重试次数，就允许兜底任务再次唤醒
        return (ExternalDocumentSyncStatus.RETRY_WAIT.name().equals(sync.getSyncStatus())
                || ExternalDocumentSyncStatus.FAILED.name().equals(sync.getSyncStatus()))
                && Optional.ofNullable(sync.getAttemptCount()).orElse(0) < maxAttempts;
    }

    /**
     * 查询当前分页文件对应的同步记录快照
     *
     * @param files 当前分页文件列表
     * @return 按外部文件 ID 分组的同步记录
     */
    private Map<String, ExternalDocumentSyncDO> syncMap(List<ExternalSourceFileDO> files) {
        List<String> fileIds = files.stream()
                .map(ExternalSourceFileDO::getId)
                .filter(StringUtils::hasText)
                .toList();
        if (fileIds.isEmpty()) {
            // 当前分页没有有效文件 ID 时直接返回空快照，避免生成无意义的 in 查询
            return Map.of();
        }
        // 同一个 externalFileId 理论上有唯一约束，合并函数只是防御异常脏数据导致 collect 失败
        return syncMapper.selectList(new LambdaQueryWrapper<ExternalDocumentSyncDO>()
                        .in(ExternalDocumentSyncDO::getExternalFileId, fileIds))
                .stream()
                .collect(Collectors.toMap(ExternalDocumentSyncDO::getExternalFileId, Function.identity(),
                        (left, right) -> left));
    }

    /**
     * 第三方系统可学习文件基础查询条件
     *
     * @return 基础查询条件
     */
    private LambdaQueryWrapper<ExternalSourceFileDO> learnableQuery() {
        // libraryType 有值是第三方系统约定的学习标记，未删除且有学习标记的文件才进入适配器
        return new LambdaQueryWrapper<ExternalSourceFileDO>()
                .eq(ExternalSourceFileDO::getIsDelete, 0)
                .isNotNull(ExternalSourceFileDO::getLibraryType)
                .ne(ExternalSourceFileDO::getLibraryType, "");
    }
}
