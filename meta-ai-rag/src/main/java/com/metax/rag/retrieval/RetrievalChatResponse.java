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
 * references 只包含 documentId 和 documentName
 * files 只包含会话级临时文件的 fileId 和 fileName
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
 *       "documentId": "1938200000000000001",
 *       "documentName": "demo.docx"
 *     }
 *   ],
 *   "files": [
 *     {
 *       "fileId": "2063846120613888002",
 *       "fileName": "upload.pdf",
 *       "documentType": "pdf"
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
        List<RetrievalCitation> references,
        /**
         * 本次参与文件上下文的临时文件列表
         */
        @Schema(description = "本次参与文件上下文的临时文件列表")
        List<MetaContextFile> files
) {

    public RetrievalChatResponse(String answer, String conversationId, List<RetrievalCitation> references) {
        this(answer, conversationId, references, List.of());
    }
}
