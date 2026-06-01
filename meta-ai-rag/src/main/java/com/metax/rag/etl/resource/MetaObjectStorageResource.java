package com.metax.rag.etl.resource;

import com.metax.rag.storage.RustFsStorageService;
import org.springframework.core.io.AbstractResource;

import java.io.InputStream;

/**
 * MetaObjectStorageResource .
 *
 * <p>
 * 对象存储 Resource，当前底层复用 RustFS S3 兼容访问服务
 * 后续接入 OSS 或其他 S3 兼容存储时，Reader 链路不需要变化
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
public class MetaObjectStorageResource extends AbstractResource {

    private final RustFsStorageService storageService;

    private final String bucket;

    private final String objectKey;

    public MetaObjectStorageResource(RustFsStorageService storageService, String bucket, String objectKey) {
        this.storageService = storageService;
        this.bucket = bucket;
        this.objectKey = objectKey;
    }

    @Override
    public String getDescription() {
        return "Object storage resource [%s/%s]".formatted(bucket, objectKey);
    }

    @Override
    public String getFilename() {
        int index = objectKey == null ? -1 : objectKey.lastIndexOf('/');
        return index < 0 ? objectKey : objectKey.substring(index + 1);
    }

    @Override
    public InputStream getInputStream() {
        return storageService.getObject(bucket, objectKey);
    }
}

