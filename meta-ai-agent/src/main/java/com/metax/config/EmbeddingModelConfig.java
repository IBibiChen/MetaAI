package com.metax.config;

import org.springframework.context.annotation.Configuration;

/**
 * EmbeddingModelConfig .
 *
 * <p>
 * 三套向量模型 (DashScope / Ollama / OpenAI 兼容 vLLM / TEI 等) 均由各自 starter 自动装配
 * 不设置 spring.ai.model.embedding 单选开关，让三套 EmbeddingModel 同时存在，使用时通过具体 bean 名显式指定
 *
 * <p>
 * EmbeddingModel 负责把文本转换成向量，VectorStore 负责存储向量、metadata，并按相似度召回文档
 * 写入和查询必须使用同一个 EmbeddingModel 对应的语义空间，否则相似度计算没有可比性
 * 不同 provider 的向量维度和分布可能不同，禁止混用同一个 collection / index / prefix
 *
 * <p>
 * 为何不手动 @Bean：保留官方自动装配的连接、重试、观测和 provider options 处理
 * 向量库与 embedding 的绑定在各 VectorStore 配置类中完成，每套 embedding 使用独立存储单元
 *
 * <p>
 * RedisVectorStoreConfig 通过 dashscopeEmbeddingModel / openAiEmbeddingModel / ollamaEmbeddingModel 绑定三套 Redis 向量库
 * QdrantVectorStoreConfig 通过相同 EmbeddingModel bean 绑定三套 Qdrant collection
 * MilvusVectorStoreConfig 通过相同 EmbeddingModel bean 绑定三套 Milvus collection，并要求 embeddingDimension 与模型输出维度一致
 *
 * <p>
 * RAG ChatClient 不直接绑定 EmbeddingModel，而是绑定已经构造好的 provider + backend 专属 VectorStore
 * 例如 DashScope + Redis 使用 dashScopeRedisVectorStore，OpenAI + Milvus 使用 openAiMilvusVectorStore
 *
 * <p>
 * 存在多个 EmbeddingModel bean 后禁止裸类型注入，必须使用 @Qualifier 或具体类型 / bean 名区分
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/28
 */
@Configuration
public class EmbeddingModelConfig {

}
