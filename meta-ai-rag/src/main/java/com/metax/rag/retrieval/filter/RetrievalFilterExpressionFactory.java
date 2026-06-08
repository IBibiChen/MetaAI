package com.metax.rag.retrieval.filter;

import com.metax.rag.config.RagProperties;
import com.metax.rag.model.DocumentVisibility;
import com.metax.rag.model.MetadataKeys;
import com.metax.rag.retrieval.model.RetrievalOptions;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * RetrievalFilterExpressionFactory .
 *
 * <p>
 * RAG 过滤表达式工厂，默认使用结构化参数生成 portable Filter.Expression
 * 原始 filterExpression 只保留给 trace 展示，业务接口必须使用白名单结构化字段生成实际过滤
 *
 * <p>
 * 设计说明：企业级 RAG 不应该默认暴露任意 filterExpression
 * filterExpression 很灵活，但直接暴露给普通接口会带来字段拼写错误、越权过滤和跨库兼容问题
 * 当前类使用 scope、tenantId、kbId、visibility、deptId、userId、documentId、documentType 这些结构化字段生成过滤表达式
 *
 * <p>
 * 结构化参数示例
 * <pre>{@code
 * RetrievalOptions.builder()
 *     .tenantId("t1")
 *     .kbId("kb1")
 *     .documentId("doc-001")
 *     .documentType("markdown")
 *     .userId("u1")
 *     .deptIds(List.of("d1"))
 *     .topK(5)
 *     .similarityThreshold(0.5)
 *     .query("query")
 *     .build()
 * }</pre>
 *
 * <p>
 * 生成过滤效果
 * <pre>{@code
 * scope == 'knowledge'
 * && tenantId == 't1'
 * && kbId == 'kb1'
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

    private final RagProperties properties;

    public RetrievalFilterExpressionFactory(RagProperties properties) {
        this.properties = properties;
    }

    /**
     * 构造 RAG 检索过滤表达式
     *
     * <p>
     * 原始 filterExpression 不参与实际检索，避免绕过结构化权限过滤
     *
     * @param options RAG 检索参数
     * @return Filter.Expression
     */
    public Filter.Expression create(RetrievalOptions options) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        // 阶段 1：先构造 tenantId 和 kbId 强约束，缺失时直接阻断检索
        // 先生成租户和知识库边界，再追加权限过滤和文档级收窄条件
        FilterExpressionBuilder.Op expression = requiredExpression(builder, options);
        if (properties.getRetrieval().isPermissionFilterEnabled()) {
            // 阶段 2：按配置追加 PUBLIC / DEPT / USER 可见性过滤
            // 权限过滤关闭时只保留租户、知识库和可选文档级收窄条件
            expression = builder.and(expression, permissionExpression(builder, options));
        }
        if (StringUtils.hasText(options.getDocumentId())) {
            // 阶段 3：按 documentId 收窄到指定文档
            // documentId 是可选收窄条件，适合只问某一份文档的问题
            expression = builder.and(expression, builder.eq(MetadataKeys.DOCUMENT_ID, options.getDocumentId()));
        }
        if (StringUtils.hasText(options.getDocumentType())) {
            // 阶段 4：按 documentType 收窄到指定文档类型
            // documentType 是可选收窄条件，适合只检索 markdown、pdf、json 等某类知识
            expression = builder.and(expression, builder.eq(MetadataKeys.DOCUMENT_TYPE, options.getDocumentType()));
        }
        return expression.build();
    }

    /**
     * 构造租户和知识库强约束
     *
     * <p>
     * tenantId 和 kbId 是检索隔离边界，缺少任一字段都必须快速失败
     *
     * @param builder 过滤表达式构造器
     * @param options RAG 检索参数
     * @return 租户和知识库过滤表达式
     */
    private FilterExpressionBuilder.Op requiredExpression(FilterExpressionBuilder builder, RetrievalOptions options) {
        // tenantId 和 kbId 是默认强约束，少了这两个字段就无法保证多租户和多知识库隔离
        if (!StringUtils.hasText(options.getTenantId())) {
            // 这里选择快速失败，不让缺少租户边界的请求进入向量检索
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (!StringUtils.hasText(options.getKbId())) {
            // 知识库边界缺失会导致同租户下跨知识库召回，必须阻断
            throw new IllegalArgumentException("kbId must not be blank");
        }
        return builder.and(
                builder.eq(MetadataKeys.SCOPE, MetadataKeys.SCOPE_KNOWLEDGE),
                builder.and(
                        builder.eq(MetadataKeys.TENANT_ID, options.getTenantId()),
                        builder.eq(MetadataKeys.KB_ID, options.getKbId())));
    }

    /**
     * 构造文档可见性权限过滤
     *
     * <p>
     * 默认允许 PUBLIC 文档，传入 deptIds 时追加 DEPT 文档，传入 userId 时追加 USER 文档
     *
     * @param builder 过滤表达式构造器
     * @param options RAG 检索参数
     * @return 文档可见性过滤表达式
     */
    private FilterExpressionBuilder.Op permissionExpression(FilterExpressionBuilder builder, RetrievalOptions options) {
        // PUBLIC 是所有已授权请求都能访问的基线权限
        FilterExpressionBuilder.Op expression = builder.eq(MetadataKeys.VISIBILITY, DocumentVisibility.PUBLIC.name());
        if (options.getDeptIds() != null && !options.getDeptIds().isEmpty()) {
            // deptIds 来自当前用户可访问部门列表，空字符串必须剔除，避免生成无意义的 in 条件
            List<Object> deptIds = options.getDeptIds().stream()
                    .filter(StringUtils::hasText)
                    .map(Object.class::cast)
                    .toList();
            if (!deptIds.isEmpty()) {
                // DEPT 文档必须同时满足 visibility = DEPT 和 deptId 命中当前用户可访问部门
                FilterExpressionBuilder.Op deptExpression = builder.and(
                        builder.eq(MetadataKeys.VISIBILITY, DocumentVisibility.DEPT.name()),
                        builder.in(MetadataKeys.DEPT_ID, deptIds));
                // 权限之间是 OR 关系：PUBLIC 文档或当前用户部门可见文档都允许召回
                expression = builder.or(expression, deptExpression);
            }
        }
        if (StringUtils.hasText(options.getUserId())) {
            // USER 文档必须同时满足 visibility = USER 和 userId 等于当前用户
            FilterExpressionBuilder.Op userExpression = builder.and(
                    builder.eq(MetadataKeys.VISIBILITY, DocumentVisibility.USER.name()),
                    builder.eq(MetadataKeys.USER_ID, options.getUserId()));
            // 用户私有文档追加到已授权集合，不能和 PUBLIC / DEPT 使用 AND 组合
            expression = builder.or(expression, userExpression);
        }
        return expression;
    }
}
