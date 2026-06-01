package com.metax.rag.etl.model;

/**
 * DocumentSourceType .
 *
 * <p>
 * RAG 文档来源类型，只区分文件来自对象存储还是受控本地目录
 * RAG 索引链路只消费已经归档好的文件资源
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
public enum DocumentSourceType {

    /**
     * 对象存储文件流，覆盖 RustFS、OSS 和 S3
     */
    OBJECT_STORAGE,

    /**
     * 受控本地目录文件
     */
    LOCAL_FILE
}

