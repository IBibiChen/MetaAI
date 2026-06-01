package com.metax.rag.retrieval;

import java.util.List;

/**
 * RetrievalChatResponse .
 *
 * <p>
 * RAG 详情响应，保留最终回答和检索引用，便于调试命中质量和前端展示来源
 *
 * <p>
 * 字段说明：普通 RAG 接口只返回 answer，details 接口额外返回 references
 * references 可以用于判断是否召回到了正确文档、chunk 是否过大或过碎、filter 是否生效
 * trace 用于排查 query 转换、检索过滤、召回数量和后处理数量
 *
 * <p>
 * 示例
 * <pre>{@code
 * {
 *   "answer": "RAG 由检索和生成两部分组成",
 *   "conversationId": "tenantId:userId:sessionId",
 *   "references": [],
 *   "trace": {
 *     "query": "Spring AI 的 RAG 是什么",
 *     "queryTransformerMode": "none",
 *     "topK": 5,
 *     "similarityThreshold": 0.5,
 *     "retrievedCount": 5,
 *     "usedCount": 3
 *   }
 * }
 * }</pre>
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
public record RetrievalChatResponse(
        /**
         * 模型最终回答
         */
        String answer,
        /**
         * 会话 ID
         */
        String conversationId,
        /**
         * 本次 RAG 检索命中的引用来源
         */
        List<RetrievalReference> references,
        /**
         * 本次 RAG 检索链路 trace，普通接口不返回
         */
        RetrievalTrace trace
) {

    public RetrievalChatResponse(String answer, String conversationId, List<RetrievalReference> references) {
        this(answer, conversationId, references, null);
    }
}
