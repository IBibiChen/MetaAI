package com.metax.storage.event;

import com.metax.rag.indexing.DocumentIndexingSubmission;

/**
 * StorageDocumentIndexingEvent .
 *
 * <p>
 * 对象存储文档索引启动事件，由 storage 事务提交后触发真正的 RAG ETL
 *
 * @param submission 索引提交结果
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/23
 */
public record StorageDocumentIndexingEvent(
        DocumentIndexingSubmission submission
) {
}
