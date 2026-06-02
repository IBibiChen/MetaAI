package com.metax.rag.indexing;

import java.time.Instant;
import java.util.UUID;

/**
 * DocumentIndexingJob .
 *
 * <p>
 * RAG 文档索引任务快照，记录文件来源、目标向量库、chunk 数量和错误信息
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
         * 对象存储 bucket 名称
         */
        String bucket,
        /**
         * 对象存储 object key
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

    /**
     * 创建待执行文档索引任务
     *
     * <p>
     * pending 表示请求已经进入索引队列，但异步 Worker 还没有开始处理
     * 这里根据已解析请求创建 jobId、初始状态、初始时间和文件来源字段
     *
     * @param request 已解析的文档索引请求
     * @return 待执行文档索引任务
     */
    public static DocumentIndexingJob pending(DocumentIndexingRequest request) {
        Instant now = Instant.now();
        return new DocumentIndexingJob(UUID.randomUUID().toString(), DocumentIndexingStatus.PENDING,
                request.tenantId(), request.knowledgeBaseId(), request.documentId(), request.documentType(),
                request.provider().apiName(), request.vectorStore().apiName(), request.bucket(), request.objectKey(),
                0, "RAG document indexing submitted", now, now);
    }

    /**
     * 创建状态流转后的文档索引任务快照
     *
     * @param status     文档索引任务状态
     * @param chunkCount 成功写入的 chunk 数量
     * @param message    任务状态说明或失败原因
     * @return 文档索引任务快照
     */
    public DocumentIndexingJob withStatus(DocumentIndexingStatus status, int chunkCount, String message) {
        // record 不可变，状态流转通过创建新快照完成，避免多线程异步任务修改同一个对象
        return new DocumentIndexingJob(jobId, status, tenantId, knowledgeBaseId, documentId, documentType,
                provider, vectorStore, bucket, objectKey, chunkCount, message, createdAt, Instant.now());
    }
}
