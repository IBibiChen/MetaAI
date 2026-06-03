package com.metax.rag.storage;

import java.io.InputStream;

/**
 * ObjectStorageClient .
 *
 * <p>
 * S3 兼容对象存储客户端端口，负责对象上传、读取、探测和删除
 * RustFS、MinIO 和其他 S3 兼容存储都通过该端口接入
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
public interface ObjectStorageClient {

    /**
     * 上传对象
     *
     * @param bucket      bucket 名称
     * @param objectKey   object key
     * @param inputStream 对象输入流
     * @param size        对象大小
     * @param contentType 内容类型
     * @return 上传结果
     */
    StoredObject putObject(String bucket, String objectKey, InputStream inputStream, long size, String contentType);

    /**
     * 读取对象输入流
     *
     * <p>
     * 返回 InputStream 后由调用方负责关闭
     *
     * @param bucket    bucket 名称
     * @param objectKey object key
     * @return 对象输入流
     */
    InputStream getObject(String bucket, String objectKey);

    /**
     * 探测对象元数据
     *
     * @param bucket    bucket 名称
     * @param objectKey object key
     * @return 对象元数据
     */
    StoredObject headObject(String bucket, String objectKey);

    /**
     * 删除对象
     *
     * @param bucket    bucket 名称
     * @param objectKey object key
     */
    void deleteObject(String bucket, String objectKey);

    /**
     * 默认 bucket 名称
     *
     * @return 默认 bucket 名称
     */
    String defaultBucket();
}
