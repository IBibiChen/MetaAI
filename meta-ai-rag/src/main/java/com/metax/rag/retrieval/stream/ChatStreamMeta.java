package com.metax.rag.retrieval.stream;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ChatStreamMeta .
 *
 * <p>
 * 流式对话开始事件数据
 *
 * @param chatId 会话 ID
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@Schema(description = "流式对话开始事件数据")
public record ChatStreamMeta(
        /**
         * 会话 ID
         */
        @Schema(description = "会话 ID", example = "t1-u1-s1")
        String chatId
) {
}
