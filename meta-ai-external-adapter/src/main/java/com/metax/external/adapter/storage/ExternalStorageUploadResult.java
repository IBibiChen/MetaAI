package com.metax.external.adapter.storage;

/**
 * ExternalStorageUploadResult .
 *
 * <p>
 * 对象存储文档上传结果
 *
 * @param documentId  文档 ID
 * @param indexStatus 索引状态
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/11
 */
public record ExternalStorageUploadResult(
        String documentId,
        String indexStatus
) {
}
