package com.metax.rag.retrieval;

/**
 * RetrievalOptions .
 *
 * <p>
 * RAG 检索运行时参数，默认值来自配置文件，请求侧只覆盖确实需要调整的字段
 *
 * <p>
 * 字段说明：检索参数分为过滤参数和召回参数
 * tenantId / knowledgeBaseId / documentId / documentType 用于 metadata filter
 * topK / similarityThreshold 用于控制召回数量和相似度下限
 * filterExpression 是高级调试入口，普通业务接口应优先使用结构化字段
 *
 * <p>
 * 示例
 * <pre>{@code
 * new RetrievalOptions("t1", "kb1", null, "markdown", 5, 0.5, null)
 * }</pre>
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
public record RetrievalOptions(
        /**
         * 租户 ID，结构化过滤的必填字段
         */
        String tenantId,
        /**
         * 知识库 ID，结构化过滤的必填字段
         */
        String knowledgeBaseId,
        /**
         * 文档 ID，可选收窄条件
         */
        String documentId,
        /**
         * 文档类型，可选收窄条件
         */
        String documentType,
        /**
         * 请求级 topK 覆盖值，为空时使用配置默认值
         */
        Integer topK,
        /**
         * 请求级相似度阈值覆盖值，为空时使用配置默认值
         */
        Double similarityThreshold,
        /**
         * 高级原始过滤表达式，普通业务接口优先使用结构化字段
         */
        String filterExpression
) {
}
