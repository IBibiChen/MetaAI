package com.metax.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClientConfig .
 *
 * <p>
 * 当前项目运行时只启用一套 ChatModel、一套 VectorStore，并默认使用 Redis ChatMemory
 * ChatModel 由 spring.ai.model.chat 选择，ChatMemory 默认绑定 redisChatMemory
 * VectorStore 由 spring.ai.vectorstore.type 选择，并在 RAG 请求阶段通过 RetrievalAugmentationAdvisor 使用
 *
 * <p>
 * 这里不再按 DashScope / OpenAI / Ollama、Redis / JDBC 或普通 / RAG 拆分 ChatClient
 * provider 和 vectorStore 交给配置文件选择，业务接口只使用一个基础 ChatClient
 * JDBC ChatMemory 作为候选能力保留，需要时由业务显式注入 jdbcChatMemory 创建专用 ChatClient
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/28
 */
@Configuration
public class ChatClientConfig {

    /**
     * 当前默认 ChatClient
     *
     * <p>
     * 默认对话和 RAG 对话共享该基础 client
     * 默认场景绑定当前配置选中的 ChatModel 和 redisChatMemory，适合普通多轮问答
     * RAG 场景在请求阶段追加 RetrievalAugmentationAdvisor，再使用当前配置选中的 VectorStore 做检索
     * 当前上下文存在 Redis / JDBC 两个 ChatMemory Bean，因此这里必须显式绑定 redisChatMemory
     *
     * @param model             当前配置选中的 ChatModel
     * @param chatMemory        Redis ChatMemory
     * @param chatClientFactory ChatClient 工厂
     * @return ChatClient
     */
    @Bean
    public ChatClient chatClient(ChatModel model,
                                 @Qualifier("redisChatMemory") ChatMemory chatMemory,
                                 ChatClientFactory chatClientFactory) {
        return chatClientFactory.buildClient(model, chatMemory);
    }
}
