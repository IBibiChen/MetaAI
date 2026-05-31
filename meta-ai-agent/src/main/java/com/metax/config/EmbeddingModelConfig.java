package com.metax.config;

import org.springframework.context.annotation.Configuration;

/**
 * EmbeddingModelConfig .
 *
 * <p>
 * 向量模型自动装配策略说明
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/28
 */
@Configuration
public class EmbeddingModelConfig {

    /*
     * 当前类定位
     *
     * 这是一份写在配置类内部的向量模型学习笔记
     * 当前类本身不创建任何 Bean，只记录本项目的 EmbeddingModel 自动装配策略和 VectorStore 绑定规则
     *
     * ChatModel、EmbeddingModel、VectorStore 职责
     *
     * ChatModel 负责根据 prompt 生成自然语言回答
     * EmbeddingModel 负责把文本转换成向量，不负责保存向量，也不负责相似度检索
     * VectorStore 负责保存向量和 metadata，并在查询时基于相似度召回 Document
     *
     * 文档写入链路
     *
     * Document -> EmbeddingModel -> vector -> VectorStore
     *
     * RAG 查询链路
     *
     * question -> EmbeddingModel -> query vector -> VectorStore similarity search -> Document
     * question + retrieved Document -> ChatModel -> answer
     *
     * 自动装配规则
     *
     * 当前项目同时使用 DashScope / Ollama / OpenAI 兼容三套 EmbeddingModel
     * 三套 EmbeddingModel 均由各自 Spring AI starter 根据 application.properties 自动装配
     * 当前项目不要配置 spring.ai.model.embedding，因为它是 provider 单选开关，会关闭未选中的 EmbeddingModel 自动装配
     * 当前类不手动声明 EmbeddingModel Bean，是为了保留官方 starter 的连接配置、options 映射、metadata-mode、观测和生命周期处理
     * 存在多个 EmbeddingModel bean 后禁止裸类型注入，必须使用 @Qualifier 或具体 bean 名区分
     *
     * VectorStore 绑定规则
     *
     * 项目只在 VectorStore 配置类中通过 @Qualifier 显式选择 EmbeddingModel bean
     * 向量模型和向量数据库必须成对使用
     * 同一个知识库的写入和查询必须使用同一个 EmbeddingModel 对应的语义空间
     * 不同 provider 的向量维度、分布和语义空间可能不同，禁止混用同一个 collection / index / prefix
     *
     * 向量数据库隔离方式
     *
     * RedisVectorStoreConfig 使用 indexName + prefix 隔离三套 Redis 向量数据
     * QdrantVectorStoreConfig 使用 collectionName 隔离三套 Qdrant 向量数据
     * MilvusVectorStoreConfig 使用 collectionName 隔离三套 Milvus 向量数据，并要求 embeddingDimension 与模型输出维度一致
     *
     * RAG ChatClient 绑定规则
     *
     * RAG ChatClient 不直接绑定 EmbeddingModel，而是绑定已经构造好的 provider + backend 专属 VectorStore
     * 这样可以把模型 provider 和向量数据库 backend 的组合关系固定在配置层，避免业务代码临时拼装错误链路
     * 当前项目还引入 Redis ChatMemory 和 JDBC ChatMemory 两套记忆后端，因此 RAG ChatClient 名称同时包含 memory backend 和 vector backend
     * memory backend 只影响对话历史存储位置，vector backend 只影响知识库检索位置，两者不能混为一谈
     *
     * provider + vector backend + rag client 装配图
     *
     * dashscopeEmbeddingModel -> dashScopeRedisVectorStore -> dashScopeRedisMemoryRedisRagChatClient / dashScopeJdbcMemoryRedisRagChatClient
     * dashscopeEmbeddingModel -> dashScopeQdrantVectorStore -> dashScopeRedisMemoryQdrantRagChatClient / dashScopeJdbcMemoryQdrantRagChatClient
     * dashscopeEmbeddingModel -> dashScopeMilvusVectorStore -> dashScopeRedisMemoryMilvusRagChatClient / dashScopeJdbcMemoryMilvusRagChatClient
     *
     * openAiEmbeddingModel -> openAiRedisVectorStore -> openAiRedisMemoryRedisRagChatClient / openAiJdbcMemoryRedisRagChatClient
     * openAiEmbeddingModel -> openAiQdrantVectorStore -> openAiRedisMemoryQdrantRagChatClient / openAiJdbcMemoryQdrantRagChatClient
     * openAiEmbeddingModel -> openAiMilvusVectorStore -> openAiRedisMemoryMilvusRagChatClient / openAiJdbcMemoryMilvusRagChatClient
     *
     * ollamaEmbeddingModel -> ollamaRedisVectorStore -> ollamaRedisMemoryRedisRagChatClient / ollamaJdbcMemoryRedisRagChatClient
     * ollamaEmbeddingModel -> ollamaQdrantVectorStore -> ollamaRedisMemoryQdrantRagChatClient / ollamaJdbcMemoryQdrantRagChatClient
     * ollamaEmbeddingModel -> ollamaMilvusVectorStore -> ollamaRedisMemoryMilvusRagChatClient / ollamaJdbcMemoryMilvusRagChatClient
     *
     * 禁止事项和排查方式
     *
     * 写入知识库时选择哪个 VectorStore，查询 RAG 时就必须使用同一个 VectorStore
     * 只复用 metadata key 规范，不复用不同 provider 的向量索引或 collection
     * AiBeanInspector 可用于启动后核实 EmbeddingModel 和 VectorStore 的实际 bean 名与类型
     */

}
