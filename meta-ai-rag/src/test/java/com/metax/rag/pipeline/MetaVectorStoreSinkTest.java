package com.metax.rag.pipeline;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * MetaVectorStoreSinkTest .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/1
 */
class MetaVectorStoreSinkTest {

    /**
     * sink upsert 应先删除旧 chunk 再写入新 chunk
     */
    @Test
    void shouldDeleteBeforeWrite() {
        MetaVectorStoreWriter vectorStoreWriter = mock(MetaVectorStoreWriter.class);
        Filter.Expression deleteFilter = mock(Filter.Expression.class);
        MetaVectorStoreSink sink = new MetaVectorStoreSink(vectorStoreWriter, deleteFilter);
        List<Document> documents = List.of(new Document("chunk"));

        sink.upsert(documents);

        // upsert 的顺序必须稳定为先删后写，避免旧 chunk 和新 chunk 混在同一文档范围内
        inOrder(vectorStoreWriter).verify(vectorStoreWriter).delete(deleteFilter);
        verify(vectorStoreWriter).write(documents);
    }
}
