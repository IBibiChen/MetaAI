package com.metax.chat.file;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * MetaChatFileItemResponse .
 *
 * <p>
 * 会话文件展示响应，面向前端文件条和轮询状态
 * 该对象表示文件处理状态，不等同于可参与问答的 MetaContextFile
 *
 * @param fileId       文件 ID
 * @param fileName     文件名称
 * @param documentType 文档类型
 * @param parseStatus  解析状态
 * @param chunkCount   已写入临时索引的 chunk 数
 * @param createdAt    创建时间
 * @param updatedAt    更新时间
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/10
 */
@Schema(description = "会话文件展示响应")
public record MetaChatFileItemResponse(
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
        String documentType,
        /**
         * 解析状态
         */
        @Schema(description = "解析状态", example = "PARSING")
        String parseStatus,
        /**
         * 已写入临时索引的 chunk 数
         */
        @Schema(description = "已写入临时索引的 chunk 数", example = "17")
        Integer chunkCount,
        /**
         * 创建时间
         */
        @Schema(description = "创建时间")
        Instant createdAt,
        /**
         * 更新时间
         */
        @Schema(description = "更新时间")
        Instant updatedAt
) {
}
