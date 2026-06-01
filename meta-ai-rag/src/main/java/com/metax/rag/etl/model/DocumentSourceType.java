package com.metax.rag.etl.model;

/**
 * DocumentSourceType .
 *
 * <p>
 * RAG 文档来源类型，只区分文件来自对象存储还是受控本地目录
 * 上传文件会先保存到对象存储，因此也归入 OBJECT_STORAGE
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
public enum DocumentSourceType {

    /**
     * 对象存储文件流，覆盖 RustFS、OSS、S3 和上传后落对象存储的文件
     */
    OBJECT_STORAGE,

    /**
     * 受控本地目录文件
     */
    LOCAL_FILE
}

