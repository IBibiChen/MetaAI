package com.metax.rag.model;

import java.util.Locale;

/**
 * VectorStoreBackend .
 *
 * <p>
 * RAG 向量库后端枚举，当前项目同时保留 Redis、Qdrant、Milvus 三套后端并由调用方手动选择
 *
 * <p>
 * 设计说明：backend 表示向量数据库实现，不表示 embedding provider
 * redis、qdrant、milvus 可以保存同一个 provider 生成的向量
 * 但不同后端的索引、过滤语法转换、性能特征和运维方式不同
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
public enum VectorStoreBackend {

    REDIS("redis", "Redis"),

    QDRANT("qdrant", "Qdrant"),

    MILVUS("milvus", "Milvus");

    private final String apiName;

    private final String beanSegment;

    VectorStoreBackend(String apiName, String beanSegment) {
        this.apiName = apiName;
        this.beanSegment = beanSegment;
    }

    public String apiName() {
        return apiName;
    }

    public String beanSegment() {
        return beanSegment;
    }

    public static VectorStoreBackend from(String value) {
        // API 参数使用小写名称，Bean 名片段使用配置类现有命名规范
        String normalized = value.toLowerCase(Locale.ROOT);
        for (VectorStoreBackend backend : values()) {
            if (backend.apiName.equals(normalized)) {
                return backend;
            }
        }
        throw new IllegalArgumentException("Unsupported vectorStore: " + value);
    }
}
