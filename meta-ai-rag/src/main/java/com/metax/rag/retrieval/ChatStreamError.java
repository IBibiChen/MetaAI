package com.metax.rag.retrieval;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ChatStreamError .
 *
 * <p>
 * 流式对话错误事件数据
 *
 * @param message 错误消息
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/4
 */
@Schema(description = "流式对话错误事件数据")
public record ChatStreamError(
        /**
         * 错误消息
         */
        @Schema(description = "错误消息", example = "系统异常")
        String message
) {
}
