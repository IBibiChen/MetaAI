package com.metax.rag.indexing;

/**
 * DocumentIndexingStatus .
 *
 * <p>
 * RAG 文档索引任务状态，存储在 Redis 中用于查询异步 ETL 进度
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
public enum DocumentIndexingStatus {

    PENDING,

    RUNNING,

    SUCCEEDED,

    FAILED
}
