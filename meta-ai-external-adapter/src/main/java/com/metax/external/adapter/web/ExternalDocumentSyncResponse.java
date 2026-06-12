package com.metax.external.adapter.web;

/**
 * ExternalDocumentSyncResponse .
 *
 * <p>
 * 第三方系统文件同步响应
 *
 * @param externalFileId 第三方系统文件 ID
 * @param documentId     本系统文档 ID
 * @param syncStatus     同步状态
 * @param externalStatus 第三方系统学习状态
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/11
 */
public record ExternalDocumentSyncResponse(
        String externalFileId,
        String documentId,
        String syncStatus,
        Integer externalStatus
) {
}
