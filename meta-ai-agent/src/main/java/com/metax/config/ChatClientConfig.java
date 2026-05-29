package com.metax.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClientConfig .
 *
 * <p>
 * 多套 ChatModel 共存后，框架的 ChatClient.Builder 自动装配已通过 spring.ai.chat.client.enabled=false 关闭
 * 此处只声明默认记忆对话 ChatClient，RAG 检索增强 ChatClient 按向量库后端拆分到独立配置类
 *
 * <p>
 * 三套接入按 "协议 (provider)" 划分，而非部署位置 —— 同一协议可云可本地 (如 OpenAI 协议既可接官方 API 也可接本地 vLLM)
 * 运行时的工具调用、特殊提示词或特殊模型参数由调用方注入具体 ChatModel 后用 ChatClient.builder(model) 临时构造
 *
 * <p>
 * 不预置 tool client：当前没有全局默认工具集，提前定义工具调用 client 会制造误导
 * 有工具调用需求时，应在具体业务场景中按需挂载 tools / toolCallbacks
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/28
 */
@Configuration
public class ChatClientConfig {

    /**
     * DashScope 默认记忆对话 client
     *
     * <p>
     * 默认记忆对话场景，使用 DashScopeChatModel 生成回答，绑定 ChatMemory 保存会话上下文，并挂载 SimpleLoggerAdvisor 输出调试日志
     *
     * @param model             DashScope 模型 (starter 自动装配)
     * @param chatMemory        对话记忆
     * @param chatClientFactory ChatClient 工厂
     * @return ChatClient
     */
    @Bean
    public ChatClient dashScopeChatClient(DashScopeChatModel model,
                                          ChatMemory chatMemory,
                                          ChatClientFactory chatClientFactory) {
        return chatClientFactory.buildDefaultClient(model, chatMemory);
    }

    /**
     * Ollama 默认记忆对话 client
     *
     * <p>
     * 默认记忆对话场景，使用 OllamaChatModel 生成回答，绑定 ChatMemory 保存会话上下文，并挂载 SimpleLoggerAdvisor 输出调试日志
     *
     * @param model             Ollama 模型 (starter 自动装配)
     * @param chatMemory        对话记忆
     * @param chatClientFactory ChatClient 工厂
     * @return ChatClient
     */
    @Bean
    public ChatClient ollamaChatClient(OllamaChatModel model,
                                       ChatMemory chatMemory,
                                       ChatClientFactory chatClientFactory) {
        return chatClientFactory.buildDefaultClient(model, chatMemory);
    }

    /**
     * OpenAI 默认记忆对话 client
     *
     * <p>
     * 默认记忆对话场景，使用 OpenAiChatModel 生成回答，绑定 ChatMemory 保存会话上下文，并挂载 SimpleLoggerAdvisor 输出调试日志
     *
     * @param model             OpenAI 兼容模型 (vLLM / TEI 等，starter 自动装配)
     * @param chatMemory        对话记忆
     * @param chatClientFactory ChatClient 工厂
     * @return ChatClient
     */
    @Bean
    public ChatClient openAiChatClient(OpenAiChatModel model,
                                       ChatMemory chatMemory,
                                       ChatClientFactory chatClientFactory) {
        return chatClientFactory.buildDefaultClient(model, chatMemory);
    }
}
