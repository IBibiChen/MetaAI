package com.metax.rag.retrieval.stream;

import com.metax.rag.retrieval.advisor.MetaContextFile;
import com.metax.rag.retrieval.model.RetrievalDocumentReference;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * ChatStreamDone .
 *
 * <p>
 * 流式对话完成事件数据
 *
 * @param answer     完整回答
 * @param chatId     会话 ID
 * @param references 回答引用的来源文档
 * @param files      本次参与文件上下文的临时文件列表
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
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
        @Schema(description = "会话 ID", example = "t1-u1-s1")
        String chatId,
        /**
         * 回答引用的来源文档
         */
        @Schema(description = "回答引用的来源文档")
        List<RetrievalDocumentReference> references,
        /**
         * 本次参与文件上下文的临时文件列表
         */
        @Schema(description = "本次参与文件上下文的临时文件列表")
        List<MetaContextFile> files
) {

    public ChatStreamDone(String answer, String chatId, List<RetrievalDocumentReference> references) {
        this(answer, chatId, references, List.of());
    }
}
