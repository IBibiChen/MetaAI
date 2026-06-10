package com.metax.rag.pipeline;

import com.metax.rag.config.MetaRetrievalProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MetaVectorStoreWriterTest .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/10
 */
class MetaVectorStoreWriterTest {

    /**
     * 写入器应按配置的 Document 数量拆分批次
     */
    @Test
    void shouldWriteDocumentsInConfiguredBatches() {
        TestVectorStore vectorStore = new TestVectorStore();
        MetaRetrievalProperties properties = new MetaRetrievalProperties();
        properties.getVectorStore().setWriteBatchSize(10);
        MetaVectorStoreWriter writer = new MetaVectorStoreWriter(vectorStore, properties);
        List<Document> documents = documents(25);

        writer.write(documents);

        assertThat(vectorStore.writeBatches())
                .extracting(List::size)
                .containsExactly(10, 10, 5);
    }

    /**
     * 空列表不应触发 VectorStore 写入
     */
    @Test
    void shouldSkipEmptyDocuments() {
        TestVectorStore vectorStore = new TestVectorStore();
        MetaVectorStoreWriter writer = new MetaVectorStoreWriter(vectorStore, new MetaRetrievalProperties());

        writer.write(List.of());

        assertThat(vectorStore.writeBatches()).isEmpty();
    }

    /**
     * 非法批次配置应回退到保守默认值
     */
    @Test
    void shouldFallbackDefaultBatchSizeWhenConfiguredBatchSizeInvalid() {
        TestVectorStore vectorStore = new TestVectorStore();
        MetaRetrievalProperties properties = new MetaRetrievalProperties();
        properties.getVectorStore().setWriteBatchSize(0);
        MetaVectorStoreWriter writer = new MetaVectorStoreWriter(vectorStore, properties);

        writer.write(documents(11));

        assertThat(vectorStore.writeBatches())
                .extracting(List::size)
                .containsExactly(10, 1);
    }

    /**
     * 构造指定数量的测试 Document
     *
     * @param count 文档数量
     * @return 测试 Document 列表
     */
    private List<Document> documents(int count) {
        List<Document> documents = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            documents.add(new Document("chunk-" + index));
        }
        return documents;
    }

    private static class TestVectorStore implements VectorStore {

        private final List<List<Document>> writeBatches = new ArrayList<>();

        @Override
        public void add(List<Document> documents) {
            writeBatches.add(documents);
        }

        @Override
        public void delete(List<String> idList) {
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
        }

        @Override
        public List<Document> similaritySearch(org.springframework.ai.vectorstore.SearchRequest request) {
            return List.of();
        }

        private List<List<Document>> writeBatches() {
            return writeBatches;
        }
    }
}
