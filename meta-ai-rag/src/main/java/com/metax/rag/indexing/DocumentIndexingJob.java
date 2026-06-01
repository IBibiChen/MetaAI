package com.metax.rag.indexing;

import java.time.Instant;

/**
 * DocumentIndexingJob .
 *
 * <p>
 * RAG 文档索引任务快照，记录上传文件、目标向量库、chunk 数量和错误信息
 *
 * <p>
 * 字段说明：job 是异步 ETL 的最小可观察单元
 * PENDING 表示请求已接收但 Worker 还没开始
 * RUNNING 表示正在解析、切分或写入向量库
 * SUCCEEDED 表示写入完成，chunkCount 是最终写入数量
 * FAILED 表示索引失败，message 保存失败原因
 *
 * <p>
 * 返回示例
 * <pre>{@code
 * {
 *   "jobId": "uuid",
 *   "status": "SUCCEEDED",
 *   "provider": "dashscope",
 *   "vectorStore": "redis",
 *   "chunkCount": 12,
 *   "message": "RAG document indexing succeeded"
 * }
 * }</pre>
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
public record DocumentIndexingJob(
        /**
         * 文档索引任务 ID，用于查询异步 ETL 状态
         */
        String jobId,
        /**
         * 文档索引任务状态
         */
        DocumentIndexingStatus status,
        /**
         * 租户 ID
         */
        String tenantId,
        /**
         * 知识库 ID
         */
        String knowledgeBaseId,
        /**
         * 文档 ID
         */
        String documentId,
        /**
         * 文档类型
         */
        String documentType,
        /**
         * embedding provider 名称
         */
        String provider,
        /**
         * 向量库后端名称
         */
        String vectorStore,
        /**
         * RustFS bucket 名称
         */
        String bucket,
        /**
         * RustFS object key
         */
        String objectKey,
        /**
         * 成功写入的 chunk 数量
         */
        int chunkCount,
        /**
         * 任务状态说明或失败原因
         */
        String message,
        /**
         * 任务创建时间
         */
        Instant createdAt,
        /**
         * 任务最后更新时间
         */
        Instant updatedAt
) {

    public DocumentIndexingJob withStatus(DocumentIndexingStatus status, int chunkCount, String message) {
        // record 不可变，状态流转通过创建新快照完成，避免多线程异步任务修改同一个对象
        return new DocumentIndexingJob(jobId, status, tenantId, knowledgeBaseId, documentId, documentType,
                provider, vectorStore, bucket, objectKey, chunkCount, message, createdAt, Instant.now());
    }
}
