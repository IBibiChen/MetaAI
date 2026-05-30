package com.metax.config;

import org.springframework.context.annotation.Configuration;

/**
 * EmbeddingModelConfig .
 *
 * <p>
 * 这是一份写在配置类里的向量模型学习笔记
 * 当前类本身不创建任何 Bean，只记录本项目的 EmbeddingModel 自动装配策略和 VectorStore 绑定规则
 *
 * <p>
 * ChatModel、EmbeddingModel、VectorStore 是三类不同职责
 * ChatModel 负责根据 prompt 生成自然语言回答
 * EmbeddingModel 负责把文本转换成向量，不负责保存向量，也不负责相似度检索
 * VectorStore 负责保存向量和 metadata，并在查询时基于相似度召回 Document
 *
 * <p>
 * 向量写入流程通常是 Document -> EmbeddingModel -> vector -> VectorStore
 * RAG 查询流程通常是 question -> EmbeddingModel -> query vector -> VectorStore similarity search -> Document
 * 最终回答流程通常是 question + retrieved Document -> ChatModel -> answer
 *
 * <p>
 * 当前项目同时使用 DashScope / Ollama / OpenAI 兼容三套 EmbeddingModel
 * 三套 EmbeddingModel 均由各自 Spring AI starter 根据 application.properties 自动装配
 * 当前项目不要配置 spring.ai.model.embedding，因为它是 provider 单选开关，会关闭未选中的 EmbeddingModel 自动装配
 *
 * <p>
 * 当前类不手动声明 EmbeddingModel Bean，是为了保留官方 starter 的连接配置、options 映射、metadata-mode、观测和生命周期处理
 * 项目只在 VectorStore 配置类中通过 @Qualifier 显式选择 EmbeddingModel bean
 * 存在多个 EmbeddingModel bean 后禁止裸类型注入，必须使用 @Qualifier 或具体 bean 名区分
 *
 * <p>
 * 向量模型和向量数据库必须成对使用
 * 同一个知识库的写入和查询必须使用同一个 EmbeddingModel 对应的语义空间
 * 不同 provider 的向量维度、分布和语义空间可能不同，禁止混用同一个 collection / index / prefix
 *
 * <p>
 * RedisVectorStoreConfig 使用 indexName + prefix 隔离三套 Redis 向量数据
 * QdrantVectorStoreConfig 使用 collectionName 隔离三套 Qdrant 向量数据
 * MilvusVectorStoreConfig 使用 collectionName 隔离三套 Milvus 向量数据，并要求 embeddingDimension 与模型输出维度一致
 *
 * <p>
 * RAG ChatClient 不直接绑定 EmbeddingModel，而是绑定已经构造好的 provider + backend 专属 VectorStore
 * 例如 DashScope + Redis 使用 dashScopeRedisVectorStore，OpenAI + Milvus 使用 openAiMilvusVectorStore
 * 这样可以把模型 provider 和向量数据库 backend 的组合关系固定在配置层，避免业务代码临时拼装错误链路
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/28
 */
@Configuration
public class EmbeddingModelConfig {

    /*
     * 项目装配图
     *
     * dashscopeEmbeddingModel -> dashScopeRedisVectorStore -> dashScopeRedisRagChatClient
     * dashscopeEmbeddingModel -> dashScopeQdrantVectorStore -> dashScopeQdrantRagChatClient
     * dashscopeEmbeddingModel -> dashScopeMilvusVectorStore -> dashScopeMilvusRagChatClient
     *
     * openAiEmbeddingModel -> openAiRedisVectorStore -> openAiRedisRagChatClient
     * openAiEmbeddingModel -> openAiQdrantVectorStore -> openAiQdrantRagChatClient
     * openAiEmbeddingModel -> openAiMilvusVectorStore -> openAiMilvusRagChatClient
     *
     * ollamaEmbeddingModel -> ollamaRedisVectorStore -> ollamaRedisRagChatClient
     * ollamaEmbeddingModel -> ollamaQdrantVectorStore -> ollamaQdrantRagChatClient
     * ollamaEmbeddingModel -> ollamaMilvusVectorStore -> ollamaMilvusRagChatClient
     *
     * 写入知识库时选择哪个 VectorStore，查询 RAG 时就必须使用同一个 VectorStore
     * 只复用 metadata key 规范，不复用不同 provider 的向量索引或 collection
     * AiBeanInspector 可用于启动后核实 EmbeddingModel 和 VectorStore 的实际 bean 名与类型
     */

}
