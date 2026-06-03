package com.metax.rag.retrieval;

import com.metax.rag.config.RagProperties;
import com.metax.rag.model.MetadataKeys;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RetrievalSearchServiceTest .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
class RetrievalSearchServiceTest {

    /**
     * 直接检索应返回向量库命中结果
     */
    @Test
    void shouldReturnVectorStoreSearchHits() {
        RagProperties properties = new RagProperties();
        RetrievalSearchService service = new RetrievalSearchService(properties,
                new RetrievalFilterExpressionFactory(properties));
        TestVectorStore vectorStore = new TestVectorStore();
        RetrievalOptions options = RetrievalOptions.builder()
                .tenantId("t1")
                .knowledgeBaseId("kb1")
                .documentId("doc1")
                .deptIds(List.of())
                .topK(3)
                .similarityThreshold(0.0)
                .query("查询内容")
                .build();

        RetrievalSearchResponse response = service.search(vectorStore, options);

        assertThat(response.query()).isEqualTo("查询内容");
        assertThat(response.topK()).isEqualTo(3);
        assertThat(response.similarityThreshold()).isEqualTo(0.0);
        assertThat(response.hitCount()).isEqualTo(1);
        assertThat(response.hits()).hasSize(1);
        assertThat(response.hits().get(0).metadata()).containsEntry(MetadataKeys.DOCUMENT_ID, "doc1");
        assertThat(response.hits().get(0).metadata()).containsEntry(MetadataKeys.VISIBILITY, "PUBLIC");
        assertThat(response.hits().get(0).metadata()).containsEntry(MetadataKeys.FILENAME, "demo.docx");
        assertThat(response.hits().get(0).downloadUrl()).isEqualTo("storage/t1/kb1/demo.docx");
        assertThat(vectorStore.searchRequest.getQuery()).isEqualTo("查询内容");
        assertThat(vectorStore.searchRequest.getTopK()).isEqualTo(3);
        assertThat(vectorStore.searchRequest.getSimilarityThreshold()).isEqualTo(0.0);
        assertThat(vectorStore.searchRequest.getFilterExpression()).isNotNull();
    }

    private static class TestVectorStore implements VectorStore {

        private SearchRequest searchRequest;

        @Override
        public void add(List<Document> documents) {
        }

        @Override
        public void delete(List<String> idList) {
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            this.searchRequest = request;
            Document document = Document.builder()
                    .text("命中 chunk")
                    .metadata(Map.of(MetadataKeys.DOCUMENT_ID, "doc1",
                            MetadataKeys.VISIBILITY, "PUBLIC",
                            MetadataKeys.FILENAME, "demo.docx",
                            MetadataKeys.SOURCE, "storage/t1/kb1/demo.docx"))
                    .score(0.9)
                    .build();
            return List.of(document);
        }
    }
}
