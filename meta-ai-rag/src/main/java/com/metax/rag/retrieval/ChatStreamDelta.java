package com.metax.rag.retrieval;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ChatStreamDelta .
 *
 * <p>
 * 流式对话增量文本事件数据
 *
 * @param content 增量文本
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/4
 */
@Schema(description = "流式对话增量文本事件数据")
public record ChatStreamDelta(
        /**
         * 增量文本
         */
        @Schema(description = "增量文本", example = "建设方案")
        String content
) {
}
