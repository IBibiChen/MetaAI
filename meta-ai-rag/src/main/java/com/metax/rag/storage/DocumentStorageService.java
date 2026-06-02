package com.metax.rag.storage;

import java.io.InputStream;

/**
 * DocumentStorageService .
 *
 * <p>
 * RAG 文档存储端口，负责读取已经归档好的知识库原始文件
 * ETL 链路只依赖该接口，不直接依赖 RustFS、MinIO 或老系统文件服务
 *
 * <p>
 * 设计边界：对象存储负责保存原始文件，VectorStore 负责保存 chunk 文本、向量和 metadata
 * 两者通过 source、bucket、objectKey 建立关联，便于后续重新切分、重建索引或审计来源
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/2
 */
public interface DocumentStorageService {

    /**
     * 读取对象输入流
     *
     * <p>
     * 返回 InputStream 后由调用方负责关闭
     * MetaObjectStorageResource 会在 Reader 真正读取时打开流，并交给 Spring Resource 读取链路关闭
     *
     * @param bucket    bucket 名称
     * @param objectKey object key
     * @return 对象输入流
     */
    InputStream getObject(String bucket, String objectKey);

    /**
     * 默认 bucket 名称
     *
     * @return 默认 bucket 名称
     */
    String defaultBucket();
}
