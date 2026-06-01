package com.metax.rag.etl.resource;

import com.metax.rag.storage.RustFsStorageService;
import org.springframework.core.io.AbstractResource;
import org.springframework.lang.NonNull;

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

    /**
     * 返回资源描述，用于日志和异常提示
     *
     * @return 资源描述
     */
    @Override
    @NonNull
    public String getDescription() {
        return "Object storage resource [%s/%s]".formatted(bucket, objectKey);
    }

    /**
     * 从 objectKey 末尾解析文件名
     *
     * @return 文件名
     */
    @Override
    @NonNull
    public String getFilename() {
        int index = objectKey == null ? -1 : objectKey.lastIndexOf('/');
        return index < 0 ? objectKey : objectKey.substring(index + 1);
    }

    /**
     * 读取对象存储文件流
     *
     * <p>
     * 这里在 Reader 真正读取时才打开对象流，避免资源创建阶段提前加载大文件
     *
     * @return 对象输入流
     */
    @Override
    @NonNull
    public InputStream getInputStream() {
        return storageService.getObject(bucket, objectKey);
    }
}

