package com.metax.storage;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * StorageDocumentUploadResponse .
 *
 * <p>
 * 对象存储文档上传响应
 *
 * @param documentId          文档 ID
 * @param originalFilename    原始文件名
 * @param visibility          文档可见性
 * @param deptId              部门 ID
 * @param userId              用户 ID
 * @param bucket              对象存储 bucket
 * @param objectKey           对象存储 object key
 * @param fileSize            文件大小
 * @param fileSha256          文件 SHA-256
 * @param documentType        文档类型
 * @param indexStatus         索引状态
 * @param chunkCount          索引 chunk 数
 * @param latestIndexingRunId 最新索引执行 ID
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
@Schema(description = "对象存储文档上传响应")
public record StorageDocumentUploadResponse(
        @Schema(description = "文档 ID", example = "1938200000000000001")
        String documentId,
        @Schema(description = "原始文件名", example = "demo.pdf")
        String originalFilename,
        @Schema(description = "文档可见性", example = "PUBLIC")
        String visibility,
        @Schema(description = "部门 ID", example = "d1")
        String deptId,
        @Schema(description = "用户 ID", example = "u1")
        String userId,
        @Schema(description = "对象存储 bucket", example = "meta-ai-knowledge")
        String bucket,
        @Schema(description = "对象存储 object key", example = "t1/kb1/1938200000000000001/demo.pdf")
        String objectKey,
        @Schema(description = "文件大小，单位：字节", example = "10240")
        Long fileSize,
        @Schema(description = "文件 SHA-256", example = "f2c7bb8acc97f92e987a2d4087d021b1f3f178d79e56c1502d7d91a042cfb28f")
        String fileSha256,
        @Schema(description = "文档类型", example = "pdf")
        String documentType,
        @Schema(description = "索引状态", example = "INDEXED")
        String indexStatus,
        @Schema(description = "索引 chunk 数", example = "31")
        Integer chunkCount,
        @Schema(description = "最新索引执行 ID", example = "1938200000000000002")
        String latestIndexingRunId
) {
}
