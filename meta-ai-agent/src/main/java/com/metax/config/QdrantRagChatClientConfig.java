package com.metax.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * QdrantRagChatClientConfig .
 *
 * <p>
 * Qdrant RAG 检索增强 ChatClient 配置，三套 client 分别绑定对应 provider 的 ChatModel 和 QdrantVectorStore
 * 每套 provider 再按 Redis 记忆和 JDBC 记忆拆分为两个 ChatClient，调用方按 Bean 名手动选择
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/29
 */
@Configuration
public class QdrantRagChatClientConfig {

    /**
     * DashScope QdrantVectorStore + Redis 记忆 RAG 检索增强 client
     *
     * <p>
     * RAG 检索增强场景，使用 DashScopeChatModel 生成回答，绑定 redisChatMemory 维护会话，并绑定 dashScopeQdrantVectorStore 检索同协议 embedding 写入的 Qdrant 知识库内容
     *
     * @param model             DashScope 模型 (starter 自动装配)
     * @param chatMemory        Redis 对话记忆
     * @param vectorStore       DashScope Qdrant 向量库
     * @param chatClientFactory ChatClient 工厂
     * @return ChatClient
     */
    @Bean
    public ChatClient dashScopeRedisMemoryQdrantRagChatClient(DashScopeChatModel model,
                                                              @Qualifier("redisChatMemory") ChatMemory chatMemory,
                                                              @Qualifier("dashScopeQdrantVectorStore") VectorStore vectorStore,
                                                              ChatClientFactory chatClientFactory) {
        return chatClientFactory.buildRagClient(model, chatMemory, vectorStore);
    }

    /**
     * DashScope QdrantVectorStore + JDBC 记忆 RAG 检索增强 client
     *
     * <p>
     * RAG 检索增强场景，使用 DashScopeChatModel 生成回答，绑定 jdbcChatMemory 维护会话，并绑定 dashScopeQdrantVectorStore 检索同协议 embedding 写入的 Qdrant 知识库内容
     *
     * @param model             DashScope 模型 (starter 自动装配)
     * @param chatMemory        JDBC 对话记忆
     * @param vectorStore       DashScope Qdrant 向量库
     * @param chatClientFactory ChatClient 工厂
     * @return ChatClient
     */
    @Bean
    public ChatClient dashScopeJdbcMemoryQdrantRagChatClient(DashScopeChatModel model,
                                                             @Qualifier("jdbcChatMemory") ChatMemory chatMemory,
                                                             @Qualifier("dashScopeQdrantVectorStore") VectorStore vectorStore,
                                                             ChatClientFactory chatClientFactory) {
        return chatClientFactory.buildRagClient(model, chatMemory, vectorStore);
    }

    /**
     * Ollama QdrantVectorStore + Redis 记忆 RAG 检索增强 client
     *
     * <p>
     * RAG 检索增强场景，使用 OllamaChatModel 生成回答，绑定 redisChatMemory 维护会话，并绑定 ollamaQdrantVectorStore 检索同协议 embedding 写入的 Qdrant 知识库内容
     *
     * @param model             Ollama 模型 (starter 自动装配)
     * @param chatMemory        Redis 对话记忆
     * @param vectorStore       Ollama Qdrant 向量库
     * @param chatClientFactory ChatClient 工厂
     * @return ChatClient
     */
    @Bean
    public ChatClient ollamaRedisMemoryQdrantRagChatClient(OllamaChatModel model,
                                                           @Qualifier("redisChatMemory") ChatMemory chatMemory,
                                                           @Qualifier("ollamaQdrantVectorStore") VectorStore vectorStore,
                                                           ChatClientFactory chatClientFactory) {
        return chatClientFactory.buildRagClient(model, chatMemory, vectorStore);
    }

    /**
     * Ollama QdrantVectorStore + JDBC 记忆 RAG 检索增强 client
     *
     * <p>
     * RAG 检索增强场景，使用 OllamaChatModel 生成回答，绑定 jdbcChatMemory 维护会话，并绑定 ollamaQdrantVectorStore 检索同协议 embedding 写入的 Qdrant 知识库内容
     *
     * @param model             Ollama 模型 (starter 自动装配)
     * @param chatMemory        JDBC 对话记忆
     * @param vectorStore       Ollama Qdrant 向量库
     * @param chatClientFactory ChatClient 工厂
     * @return ChatClient
     */
    @Bean
    public ChatClient ollamaJdbcMemoryQdrantRagChatClient(OllamaChatModel model,
                                                          @Qualifier("jdbcChatMemory") ChatMemory chatMemory,
                                                          @Qualifier("ollamaQdrantVectorStore") VectorStore vectorStore,
                                                          ChatClientFactory chatClientFactory) {
        return chatClientFactory.buildRagClient(model, chatMemory, vectorStore);
    }

    /**
     * OpenAI QdrantVectorStore + Redis 记忆 RAG 检索增强 client
     *
     * <p>
     * RAG 检索增强场景，使用 OpenAI 协议兼容模型生成回答，绑定 redisChatMemory 维护会话，并绑定 openAiQdrantVectorStore 检索同协议 embedding 写入的 Qdrant 知识库内容
     *
     * @param model             OpenAI 兼容模型 (vLLM / TEI 等，starter 自动装配)
     * @param chatMemory        Redis 对话记忆
     * @param vectorStore       OpenAI Qdrant 向量库
     * @param chatClientFactory ChatClient 工厂
     * @return ChatClient
     */
    @Bean
    public ChatClient openAiRedisMemoryQdrantRagChatClient(OpenAiChatModel model,
                                                           @Qualifier("redisChatMemory") ChatMemory chatMemory,
                                                           @Qualifier("openAiQdrantVectorStore") VectorStore vectorStore,
                                                           ChatClientFactory chatClientFactory) {
        return chatClientFactory.buildRagClient(model, chatMemory, vectorStore);
    }

    /**
     * OpenAI QdrantVectorStore + JDBC 记忆 RAG 检索增强 client
     *
     * <p>
     * RAG 检索增强场景，使用 OpenAI 协议兼容模型生成回答，绑定 jdbcChatMemory 维护会话，并绑定 openAiQdrantVectorStore 检索同协议 embedding 写入的 Qdrant 知识库内容
     *
     * @param model             OpenAI 兼容模型 (vLLM / TEI 等，starter 自动装配)
     * @param chatMemory        JDBC 对话记忆
     * @param vectorStore       OpenAI Qdrant 向量库
     * @param chatClientFactory ChatClient 工厂
     * @return ChatClient
     */
    @Bean
    public ChatClient openAiJdbcMemoryQdrantRagChatClient(OpenAiChatModel model,
                                                          @Qualifier("jdbcChatMemory") ChatMemory chatMemory,
                                                          @Qualifier("openAiQdrantVectorStore") VectorStore vectorStore,
                                                          ChatClientFactory chatClientFactory) {
        return chatClientFactory.buildRagClient(model, chatMemory, vectorStore);
    }
}
