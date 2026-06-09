package com.metax.rag.retrieval.filter;

import com.metax.rag.config.MetaRetrievalProperties;
import com.metax.rag.retrieval.model.RetrievalOptions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RetrievalFilterExpressionFactoryTest .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
class RetrievalFilterExpressionFactoryTest {

    /**
     * 默认过滤表达式只包含租户和知识库边界
     */
    @Test
    void shouldCreateFilterExpressionFromStructuredOptions() {
        RetrievalFilterExpressionFactory filterExpressionFactory = new RetrievalFilterExpressionFactory(
                new MetaRetrievalProperties());
        RetrievalOptions options = RetrievalOptions.builder()
                .tenantId("tenant-1")
                .kbId("kb-1")
                .documentId("doc-1")
                .documentType("markdown")
                .topK(5)
                .similarityThreshold(0.5)
                .build();

        assertThat(filterExpressionFactory.create(options).toString())
                .contains("tenantId")
                .contains("kbId")
                .contains("documentId")
                .contains("documentType")
                .doesNotContain("visibility");
    }

    /**
     * 权限过滤开启时公共文档过滤表达式不应包含 Group
     */
    @Test
    void shouldCreatePublicFilterExpressionWithoutGroup() {
        RetrievalFilterExpressionFactory filterExpressionFactory = new RetrievalFilterExpressionFactory(
                propertiesWithPermissionFilter());
        RetrievalOptions options = RetrievalOptions.builder()
                .tenantId("tenant-1")
                .kbId("kb-1")
                .deptIds(List.of())
                .topK(5)
                .similarityThreshold(0.5)
                .query("query")
                .build();

        assertThat(filterExpressionFactory.create(options).toString())
                .contains("PUBLIC")
                .doesNotContain("Group");
    }

    /**
     * 结构化查询参数应生成权限过滤表达式
     */
    @Test
    void shouldCreatePermissionFilterExpression() {
        RetrievalFilterExpressionFactory filterExpressionFactory = new RetrievalFilterExpressionFactory(
                propertiesWithPermissionFilter());
        RetrievalOptions options = RetrievalOptions.builder()
                .tenantId("tenant-1")
                .kbId("kb-1")
                .userId("user-1")
                .deptIds(List.of("dept-1", "dept-2"))
                .topK(5)
                .similarityThreshold(0.5)
                .query("query")
                .build();

        assertThat(filterExpressionFactory.create(options).toString())
                .contains("PUBLIC")
                .contains("DEPT")
                .contains("deptId")
                .contains("dept-1")
                .contains("USER")
                .contains("userId")
                .contains("user-1");
    }

    /**
     * 结构化过滤必须包含租户和知识库边界
     */
    @Test
    void shouldRejectMissingRetrievalScope() {
        RetrievalFilterExpressionFactory filterExpressionFactory = new RetrievalFilterExpressionFactory(
                new MetaRetrievalProperties());
        RetrievalOptions options = RetrievalOptions.builder()
                .tenantId("")
                .kbId("kb-1")
                .topK(5)
                .similarityThreshold(0.5)
                .build();

        assertThatThrownBy(() -> filterExpressionFactory.create(options))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    private MetaRetrievalProperties propertiesWithPermissionFilter() {
        MetaRetrievalProperties properties = new MetaRetrievalProperties();
        properties.getSearch().setPermissionFilterEnabled(true);
        return properties;
    }
}
