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
     * 当前项目保留 DashScope / Ollama / OpenAI 兼容三套 provider 配置，运行时只启用其中一套
     * spring.ai.model.embedding 是官方 EmbeddingModel 单选开关，用于决定当前默认 EmbeddingModel
     * spring.ai.model.chat 是官方 ChatModel 单选开关，用于决定当前默认 ChatModel
     * 当前类不手动声明 EmbeddingModel Bean，是为了保留官方 starter 的连接配置、options 映射、metadata-mode、观测和生命周期处理
     * 单套运行时推荐直接注入 EmbeddingModel，避免多套 Bean 名矩阵带来的维护成本
     *
     * VectorStore 绑定规则
     *
     * VectorStore 自动绑定当前唯一 EmbeddingModel
     * 向量模型和向量数据库必须成对使用
     * 同一个知识库的写入和查询必须使用同一个 EmbeddingModel 对应的语义空间
     * 切换 spring.ai.model.embedding 后必须同步检查向量维度、collection / index 和历史数据是否需要重建
     *
     * 向量数据库隔离方式
     *
     * spring.ai.vectorstore.type 是 Spring AI 官方 SpringAIVectorStoreTypes.TYPE 属性
     * Redis / Qdrant / Milvus 官方自动配置都通过该属性判断是否启用
     * 项目必须显式配置该属性，避免多个 VectorStore starter 同时存在时因为 matchIfMissing 同时尝试启用
     * spring.ai.vectorstore.type=redis 时使用 Redis
     * spring.ai.vectorstore.type=qdrant 时使用 Qdrant
     * spring.ai.vectorstore.type=milvus 时使用 Milvus
     * Redis 使用 MetaRedisVectorStoreConfig 补齐 metadataFields，Qdrant / Milvus 使用官方自动装配
     *
     * ChatClient 绑定规则
     *
     * 普通 ChatClient 和 RAG ChatClient 绑定当前唯一 ChatModel 和默认 redisChatMemory
     * 两者按系统提示词边界拆分，RAG 检索增强能力仍在请求阶段通过 RetrievalAugmentationAdvisor 动态追加
     * ChatMemory 只影响模型上下文窗口，不负责完整用户历史归档
     * ChatHistory 负责用户可查看的完整历史，不影响知识库检索使用的 VectorStore
     *
     * 当前主链路装配图
     *
     * spring.ai.model.embedding -> EmbeddingModel -> vectorStore
     * spring.ai.model.chat -> ChatModel -> chatClient / ragChatClient
     * redisChatMemory -> chatClient / ragChatClient
     * spring.ai.vectorstore.type -> VectorStore -> RetrievalAugmentationAdvisor
     *
     * 禁止事项和排查方式
     *
     * 写入知识库和查询 RAG 使用同一个默认 VectorStore
     * 切换 provider 或 vectorStore 后不要直接复用旧索引，除非能证明 embedding 维度和语义空间一致
     * AiBeanInspector 可用于启动后核实 EmbeddingModel 和 VectorStore 的实际 bean 名与类型
     */

}
