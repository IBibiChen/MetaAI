package com.metax.external.adapter.sync.model;

/**
 * ExternalDocumentSyncStatus .
 *
 * <p>
 * 第三方系统文档同步内部状态
 *
 * <p>
 * 该状态只用于适配器持久队列，控制下载、上传、索引等待、重试和终态
 * 第三方源表 status 字段使用 ExternalDocumentLearningStatus 映射
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/11
 */
public enum ExternalDocumentSyncStatus {

    /**
     * 待处理
     *
     * <p>
     * 第三方文件已入队，尚未被 Worker 抢占
     */
    PENDING,

    /**
     * 下载中
     *
     * <p>
     * Worker 已抢占任务，正在从第三方文件服务下载原始文件流
     */
    DOWNLOADING,

    /**
     * 已归档
     *
     * <p>
     * 文件已进入本系统对象存储，后续可以基于 documentId 继续提交或重试索引
     */
    STORED,

    /**
     * 索引中
     *
     * <p>
     * 已提交 ETL / OCR / 向量化任务，Worker 正在等待对象存储文档进入索引终态
     */
    INDEXING,

    /**
     * 等待重试
     *
     * <p>
     * 本次处理失败但未达到最大重试次数，需要等待 nextAttemptAt 到期后再次抢占
     */
    RETRY_WAIT,

    /**
     * 已完成
     *
     * <p>
     * 对象存储文档索引状态已进入 INDEXED，第三方系统状态会回写为学习完成
     */
    INDEXED,

    /**
     * 终态失败
     *
     * <p>
     * 达到最大重试次数后仍未成功，第三方系统状态会回写为学习失败
     */
    FAILED
}
