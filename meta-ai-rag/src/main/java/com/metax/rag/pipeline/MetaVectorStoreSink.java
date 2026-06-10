package com.metax.rag.pipeline;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;

/**
 * MetaVectorStoreSink .
 *
 * <p>
 * VectorStore 写入端，封装 RAG upsert 所需的删除旧 chunk 和写入新 chunk 操作
 * MetaVectorStoreWriter 仍然委托 Spring AI VectorStore，这里只收敛项目级 upsert policy
 *
 * @param vectorStoreWriter 向量库写入器
 * @param deleteFilter      删除旧 chunk 的过滤条件
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/1
 */
public record MetaVectorStoreSink(
        MetaVectorStoreWriter vectorStoreWriter,
        Filter.Expression deleteFilter
) {

    /**
     * 删除旧 chunk 并写入新 chunk
     *
     * @param documents chunk Document 列表
     */
    public void upsert(List<Document> documents) {
        // upsert 必须先按文档范围删除旧 chunk，然后再分批写入新 chunk
        vectorStoreWriter.delete(deleteFilter);
        vectorStoreWriter.write(documents);
    }
}
