package com.metax.external.adapter.sync;

import com.metax.external.adapter.config.ExternalAdapterProperties;
import com.metax.external.adapter.source.ExternalSourceFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ExternalDocumentReconciler .
 *
 * <p>
 * 第三方系统文件同步兜底补偿协调器
 *
 * <p>
 * 该组件只负责发现漏通知文件和同步索引终态
 * 文件下载、对象存储上传、ETL 和向量化仍由 ExternalAdapterSyncWorker 串行处理
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/11
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "metax.external-adapter.enabled", havingValue = "true")
public class ExternalDocumentReconciler {

    /**
     * 第三方系统适配器配置
     *
     * <p>
     * 这里主要读取补偿开关、扫描批量大小和最大重试次数
     */
    private final ExternalAdapterProperties properties;

    /**
     * 第三方源文件访问服务
     *
     * <p>
     * 用于扫描第三方源表中需要学习但可能漏通知的文件
     */
    private final ExternalSourceFileService sourceFileService;

    /**
     * 同步编排服务
     *
     * <p>
     * 补偿器只调用它入队或同步终态，不直接处理下载、上传和索引
     */
    private final ExternalDocumentSyncService syncService;

    /**
     * 兜底补偿第三方系统已上传但未通知成功的学习文件
     *
     * <p>
     * 该任务只负责把历史文件或漏通知文件入队，不直接执行下载、上传和索引
     * 重活统一交给 ExternalAdapterSyncWorker 串行消费
     */
    @Scheduled(fixedDelayString = "${metax.external-adapter.reconcile.fixed-delay-millis:300000}")
    public void reconcileFiles() {
        if (!properties.getReconcile().isEnabled()) {
            // 补偿开关关闭时直接返回，允许只保留实时通知入口
            return;
        }
        int batchSize = properties.getReconcile().getBatchSize();
        sourceFileService.findLearnableForReconcile(batchSize, properties.getMaxAttempts()).forEach(file -> {
            try {
                // requestSync 只负责创建或刷新持久队列记录，真正重活交给单线程 Worker 串行处理
                syncService.requestSync(file.getId());
            } catch (RuntimeException ex) {
                // 单个文件补偿失败不能影响本轮其他文件，失败原因保留到 debug，避免定时任务刷屏
                log.debug("第三方系统文件补偿同步跳过：externalFileId = {}，error = {}", file.getId(), ex.getMessage());
            }
        });
    }

    /**
     * 兜底同步已提交索引任务的完成状态
     */
    @Scheduled(fixedDelayString = "${metax.external-adapter.reconcile.fixed-delay-millis:300000}")
    public void reconcileIndexStatus() {
        if (!properties.getReconcile().isEnabled()) {
            // 和文件补偿共用一个开关，关闭补偿时也关闭索引终态兜底同步
            return;
        }
        // Worker 正常会等待索引终态，这里用于兜底修复应用重启或中断后遗留的 INDEXING 记录
        syncService.reconcileIndexStatus(properties.getReconcile().getBatchSize());
    }
}
