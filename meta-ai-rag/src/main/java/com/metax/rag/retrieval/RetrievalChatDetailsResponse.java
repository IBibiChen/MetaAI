package com.metax.rag.retrieval;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * RetrievalChatDetailsResponse .
 *
 * <p>
 * RAG 详情响应，保留最终回答、完整 chunk 引用和检索链路 trace
 *
 * <p>
 * references 用于判断是否召回到了正确文档、chunk 是否过大或过碎、filter 是否生效
 * trace 用于排查 query 转换、检索过滤、召回数量和后处理数量
 * 普通 /v1/rag 不使用该响应，避免把排查字段暴露给聊天窗口
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/4
 */
@Schema(description = "RAG 检索增强详情响应")
public record RetrievalChatDetailsResponse(
        /**
         * 模型最终回答
         */
        @Schema(description = "模型最终回答", example = "建设方案主要包括平台总体架构、业务模块和实施计划")
        String answer,
        /**
         * 会话 ID
         */
        @Schema(description = "会话 ID", example = "t1:u1:s1")
        String chatId,
        /**
         * 本次 RAG 检索命中的完整引用来源
         */
        @Schema(description = "本次 RAG 检索命中的完整引用来源")
        List<RetrievalReference> references,
        /**
         * 本次 RAG 检索链路 trace
         */
        @Schema(description = "本次 RAG 检索链路 trace")
        RetrievalTrace trace
) {
}
