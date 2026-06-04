package com.metax.rag.retrieval;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * RetrievalChatResponse .
 *
 * <p>
 * 普通 RAG 对话响应，保留最终回答、会话 ID 和前端展示用文件引用
 *
 * <p>
 * references 只包含原始文件名和 documentId
 * chunk 文本、score、metadata 等排查字段只在 details / search 接口返回
 *
 * <p>
 * 示例
 * <pre>{@code
 * {
 *   "answer": "RAG 由检索和生成两部分组成",
 *   "conversationId": "tenantId:userId:sessionId",
 *   "references": [
 *     {
 *       "filename": "demo.docx",
 *       "documentId": "1938200000000000001"
 *     }
 *   ]
 * }
 * }</pre>
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
@Schema(description = "RAG 普通对话响应")
public record RetrievalChatResponse(
        /**
         * 模型最终回答
         */
        @Schema(description = "模型最终回答", example = "建设方案主要包括平台总体架构、业务模块和实施计划")
        String answer,
        /**
         * 会话 ID
         */
        @Schema(description = "会话 ID", example = "t1:u1:s1")
        String conversationId,
        /**
         * 本次 RAG 检索引用的文件列表
         */
        @Schema(description = "本次 RAG 检索引用的文件列表")
        List<RetrievalCitation> references
) {
}
