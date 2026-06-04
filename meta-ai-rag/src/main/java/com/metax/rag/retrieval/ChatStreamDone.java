package com.metax.rag.retrieval;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * ChatStreamDone .
 *
 * <p>
 * 流式对话完成事件数据
 *
 * @param answer         完整回答
 * @param conversationId 会话 ID
 * @param references     RAG 引用文件列表
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/4
 */
@Schema(description = "流式对话完成事件数据")
public record ChatStreamDone(
        /**
         * 完整回答
         */
        @Schema(description = "完整回答", example = "建设方案主要包括基础设施、公共服务和产业配套")
        String answer,
        /**
         * 会话 ID
         */
        @Schema(description = "会话 ID", example = "t1:u1:s1")
        String conversationId,
        /**
         * RAG 引用文件列表
         */
        @Schema(description = "RAG 引用文件列表")
        List<RetrievalCitation> references
) {
}
