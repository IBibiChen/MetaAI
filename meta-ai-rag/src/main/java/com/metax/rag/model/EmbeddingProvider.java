package com.metax.rag.model;

import java.util.Locale;

/**
 * EmbeddingProvider .
 *
 * <p>
 * RAG embedding provider 枚举，文档索引时选择哪个 provider，查询时就必须使用同 provider 的 VectorStore
 *
 * <p>
 * 设计说明：provider 不只是模型名字，而是 embedding 语义空间
 * DashScope、Ollama、OpenAI 即使输入同一段文本，输出向量维度和分布也可能不同
 * 所以 dashscope 写入的数据必须用 dashscope 对应的 VectorStore 查询
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
public enum EmbeddingProvider {

    DASHSCOPE("dashscope", "dashScope"),

    OLLAMA("ollama", "ollama"),

    OPENAI("openai", "openAi");

    private final String apiName;

    private final String beanPrefix;

    EmbeddingProvider(String apiName, String beanPrefix) {
        this.apiName = apiName;
        this.beanPrefix = beanPrefix;
    }

    public String apiName() {
        return apiName;
    }

    public String beanPrefix() {
        return beanPrefix;
    }

    public static EmbeddingProvider from(String value) {
        // API 参数使用小写名称，Bean 名使用项目现有驼峰前缀，两者在枚举中集中维护
        String normalized = value.toLowerCase(Locale.ROOT);
        for (EmbeddingProvider provider : values()) {
            if (provider.apiName.equals(normalized)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unsupported provider: " + value);
    }
}
