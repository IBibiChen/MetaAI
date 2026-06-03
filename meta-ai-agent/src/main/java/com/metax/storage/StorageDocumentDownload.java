package com.metax.storage;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.InputStream;

/**
 * StorageDocumentDownload .
 *
 * <p>
 * 对象存储文档下载结果
 *
 * @param filename    下载文件名
 * @param contentType 内容类型
 * @param fileSize    文件大小
 * @param inputStream 文件输入流
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
@Schema(description = "服务内部文件流下载结果")
public record StorageDocumentDownload(
        @Schema(description = "下载文件名", example = "demo.pdf")
        String filename,
        @Schema(description = "内容类型", example = "application/pdf")
        String contentType,
        @Schema(description = "文件大小，单位：字节", example = "10240")
        Long fileSize,
        @Schema(description = "文件输入流", hidden = true)
        InputStream inputStream
) {
}
