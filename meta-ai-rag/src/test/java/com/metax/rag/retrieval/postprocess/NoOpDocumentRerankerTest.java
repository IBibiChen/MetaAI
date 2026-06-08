package com.metax.rag.retrieval.postprocess;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NoOpDocumentRerankerTest .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/1
 */
class NoOpDocumentRerankerTest {

    /**
     * 空 rerank 实现应保持原始文档顺序
     */
    @Test
    void shouldKeepOriginalDocumentOrder() {
        List<Document> documents = List.of(new Document("first"), new Document("second"));

        List<Document> reranked = new NoOpDocumentReranker().rerank(new Query("query"), documents);

        assertThat(reranked).containsExactlyElementsOf(documents);
    }
}
