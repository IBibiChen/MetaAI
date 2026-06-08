package com.metax.rag.retrieval.advisor;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * MetaContextFile .
 *
 * <p>
 * 会话级文件上下文，只表示临时文件，不表示知识库文档
 *
 * @param fileId       文件 ID
 * @param fileName     文件名称
 * @param documentType 文档类型
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@Schema(description = "会话级文件上下文")
public record MetaContextFile(
        /**
         * 文件 ID
         */
        @Schema(description = "文件 ID", example = "2063846120613888002")
        String fileId,
        /**
         * 文件名称
         */
        @Schema(description = "文件名称", example = "demo.pdf")
        String fileName,
        /**
         * 文档类型
         */
        @Schema(description = "文档类型", example = "pdf")
        String documentType
) {
}
