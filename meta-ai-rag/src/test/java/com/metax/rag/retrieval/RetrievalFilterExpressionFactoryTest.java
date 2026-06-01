package com.metax.rag.retrieval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RetrievalFilterExpressionFactoryTest .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
class RetrievalFilterExpressionFactoryTest {

    private final RetrievalFilterExpressionFactory filterExpressionFactory = new RetrievalFilterExpressionFactory();

    /**
     * 结构化查询参数应生成租户和知识库过滤表达式
     */
    @Test
    void shouldCreateFilterExpressionFromStructuredOptions() {
        RetrievalOptions options = new RetrievalOptions("tenant-1", "kb-1",
                "doc-1", "markdown", 5, 0.5, null);

        assertThat(filterExpressionFactory.create(options).toString())
                .contains("tenantId")
                .contains("knowledgeBaseId")
                .contains("documentId")
                .contains("documentType");
    }
}
