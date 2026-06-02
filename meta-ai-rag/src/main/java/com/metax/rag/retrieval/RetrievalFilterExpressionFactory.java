package com.metax.rag.retrieval;

import com.metax.rag.model.MetadataKeys;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * RetrievalFilterExpressionFactory .
 *
 * <p>
 * RAG 过滤表达式工厂，默认使用结构化参数生成 portable Filter.Expression
 * 原始 filterExpression 只保留给高级调试场景，业务接口优先使用白名单字段
 *
 * <p>
 * 设计说明：企业级 RAG 不应该默认暴露任意 filterExpression
 * filterExpression 很灵活，但直接暴露给普通接口会带来字段拼写错误、越权过滤和跨库兼容问题
 * 当前类优先使用 tenantId、knowledgeBaseId、documentId、documentType 这些结构化字段生成过滤表达式
 *
 * <p>
 * 结构化参数示例
 * <pre>{@code
 * new RetrievalOptions("t1", "kb1", "doc-001", "markdown", 5, 0.5, null)
 * }</pre>
 *
 * <p>
 * 生成过滤效果
 * <pre>{@code
 * tenantId == 't1'
 * && knowledgeBaseId == 'kb1'
 * && documentId == 'doc-001'
 * && documentType == 'markdown'
 * }</pre>
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
@Component
public class RetrievalFilterExpressionFactory {

    /**
     * 构造 RAG 检索过滤表达式
     *
     * <p>
     * 如果调用方传入原始 filterExpression，这里返回 null
     * Controller 会把原始表达式通过 VectorStoreDocumentRetriever.FILTER_EXPRESSION 放进 advisor context
     *
     * @param options RAG 检索参数
     * @return Filter.Expression
     */
    public Filter.Expression create(RetrievalOptions options) {
        // 阶段 1：高级原始表达式优先交给 Controller 透传，不和结构化字段混合
        if (StringUtils.hasText(options.filterExpression())) {
            // 原始表达式由 Controller 透传到 advisor context，这里不再混合结构化条件
            return null;
        }

        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        // 阶段 2：先构造租户和知识库强约束，缺失时直接阻断检索
        // 先生成租户和知识库强约束，再追加可选的文档级收窄条件
        FilterExpressionBuilder.Op expression = requiredExpression(builder, options);
        if (StringUtils.hasText(options.documentId())) {
            // 阶段 3：按 documentId 收窄到指定文档
            // documentId 是可选收窄条件，适合只问某一份文档的问题
            expression = builder.and(expression, builder.eq(MetadataKeys.DOCUMENT_ID, options.documentId()));
        }
        if (StringUtils.hasText(options.documentType())) {
            // 阶段 4：按 documentType 收窄到指定文档类型
            // documentType 是可选收窄条件，适合只检索 markdown、pdf、json 等某类知识
            expression = builder.and(expression, builder.eq(MetadataKeys.DOCUMENT_TYPE, options.documentType()));
        }
        return expression.build();
    }

    private FilterExpressionBuilder.Op requiredExpression(FilterExpressionBuilder builder, RetrievalOptions options) {
        // tenantId 和 knowledgeBaseId 是默认强约束，少了这两个字段就无法保证多租户和多知识库隔离
        if (!StringUtils.hasText(options.tenantId())) {
            // 这里选择快速失败，不让缺少租户边界的请求进入向量检索
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (!StringUtils.hasText(options.knowledgeBaseId())) {
            // 知识库边界缺失会导致同租户下跨知识库召回，必须阻断
            throw new IllegalArgumentException("knowledgeBaseId must not be blank");
        }
        return builder.and(
                builder.eq(MetadataKeys.TENANT_ID, options.tenantId()),
                builder.eq(MetadataKeys.KNOWLEDGE_BASE_ID, options.knowledgeBaseId()));
    }
}
