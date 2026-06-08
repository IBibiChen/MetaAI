package com.metax.rag.retrieval.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * RetrievalOptions .
 *
 * <p>
 * 知识库检索运行时参数，默认值来自配置文件，请求侧只覆盖确实需要调整的字段
 *
 * <p>
 * 字段说明：检索参数分为过滤参数和召回参数
 * tenantId / kbId / userId / deptIds / documentId / documentType 用于 metadata filter
 * topK / similarityThreshold 用于控制召回数量和相似度下限
 * filterExpression 仅用于 trace 调试展示，普通业务接口使用结构化字段生成实际过滤
 * query 是原始用户问题，只用于 details trace，不参与过滤表达式生成
 *
 * <p>
 * 普通结构化检索示例
 * <pre>{@code
 * RetrievalOptions.builder()
 *     .tenantId("t1")
 *     .kbId("kb1")
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
 * details trace 示例
 * <pre>{@code
 * RetrievalOptions.builder()
 *     .tenantId("t1")
 *     .kbId("kb1")
 *     .topK(5)
 *     .similarityThreshold(0.5)
 *     .query("Spring AI 的知识库问答是什么")
 *     .build()
 * }</pre>
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
@Getter
@Builder
public final class RetrievalOptions {

    /**
     * 租户 ID，结构化过滤的必填字段
     */
    private final String tenantId;

    /**
     * 知识库 ID，结构化过滤的必填字段
     */
    private final String kbId;

    /**
     * 文档 ID，可选收窄条件
     */
    private final String documentId;

    /**
     * 文档类型，可选收窄条件
     */
    private final String documentType;

    /**
     * 当前用户 ID，用于用户私有文档过滤
     */
    private final String userId;

    /**
     * 当前用户可访问部门 ID 列表，用于部门文档过滤
     */
    @Builder.Default
    private final List<String> deptIds = List.of();

    /**
     * 请求级 topK 覆盖值，为空时使用配置默认值
     */
    private final Integer topK;

    /**
     * 请求级相似度阈值覆盖值，为空时使用配置默认值
     */
    private final Double similarityThreshold;

    /**
     * 原始过滤表达式，仅用于 trace 调试展示
     */
    private final String filterExpression;

    /**
     * 原始用户 query，只用于 details trace
     */
    private final String query;
}
