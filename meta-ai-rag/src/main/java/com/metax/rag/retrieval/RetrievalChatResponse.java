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
 *
 * <p>
 * 示例
 * <pre>{@code
 * {
 *   "answer": "RAG 由检索和生成两部分组成",
 *   "conversationId": "tenantId:userId:sessionId",
 *   "references": []
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
        List<RetrievalReference> references
) {
}
