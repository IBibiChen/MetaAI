package com.metax.rag.pipeline;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
        List<String> steps = new ArrayList<>();
        TestVectorStore vectorStore = new TestVectorStore(steps);
        MetaVectorStoreSink sink = new MetaVectorStoreSink(vectorStore, mock(Filter.Expression.class));
        List<Document> documents = List.of(new Document("chunk"));

        sink.upsert(documents);

        assertThat(steps).containsExactly("delete", "write");
        assertThat(vectorStore.writtenDocuments()).containsExactlyElementsOf(documents);
    }

    private static class TestVectorStore implements VectorStore {

        private final List<String> steps;

        private List<Document> writtenDocuments = List.of();

        TestVectorStore(List<String> steps) {
            this.steps = steps;
        }

        @Override
        public void add(List<Document> documents) {
            steps.add("write");
            writtenDocuments = documents;
        }

        @Override
        public void delete(List<String> idList) {
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
            steps.add("delete");
        }

        @Override
        public List<Document> similaritySearch(org.springframework.ai.vectorstore.SearchRequest request) {
            return List.of();
        }

        List<Document> writtenDocuments() {
            return writtenDocuments;
        }
    }
}
