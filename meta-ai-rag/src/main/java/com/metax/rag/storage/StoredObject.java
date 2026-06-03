package com.metax.rag.storage;

/**
 * StoredObject .
 *
 * <p>
 * 对象存储中的对象元数据快照
 *
 * @param bucket      bucket 名称
 * @param objectKey   object key
 * @param etag        对象 etag
 * @param versionId   对象版本 ID
 * @param size        对象大小
 * @param contentType 内容类型
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
public record StoredObject(
        String bucket,
        String objectKey,
        String etag,
        String versionId,
        Long size,
        String contentType
) {
}
