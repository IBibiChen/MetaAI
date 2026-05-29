package com.metax.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClientConfig .
 *
 * <p>
 * 多套 ChatModel 共存后, 框架的 ChatClient.Builder 自动装配已通过 spring.ai.chat.client.enabled=false 关闭,
 * 故此处对每个具体 ChatModel 子类型用 ChatClient.builder(model) 显式构造预配置 ChatClient
 *
 * <p>
 * 三套接入按 "协议 (provider)" 划分, 而非部署位置 —— 同一协议可云可本地 (如 OpenAI 协议既可接官方 API 也可接本地 vLLM)。
 * 按 "模型 × 场景" 组合: DashScope / Ollama / OpenAI (兼容 vLLM / TEI 等) 各自派生 记忆对话、无状态工具调用 等 client,
 * 另预留 RAG 检索增强 client。运行时的特殊需求由调用方注入具体 ChatModel 后用 ChatClient.builder(model) 临时构造
 *
 * <p>
 * Advisor 顺序敏感: 请求方向 Memory -> RAG -> Logger -> Model, SimpleLoggerAdvisor 置于链末端
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/28
 */
@Configuration
public class ChatClientConfig {

    /**
     * 全局默认系统提示词
     */
    private static final String DEFAULT_SYSTEM = """
            你是一个专业、严谨、友好、幽默的智能助手。
            回答问题时请优先使用中文，请用海盗的口吻回答问题。
            如果不确定，请明确说明不确定，不要编造。
            """;

    // ==================== DashScope (qwen) ====================

    /**
     * DashScope 记忆多轮对话 client
     *
     * <p>
     * 按具体类型 DashScopeChatModel 注入, 三 provider 类型互异, 无歧义
     *
     * @param model      DashScope 模型 (starter 自动装配)
     * @param chatMemory 对话记忆
     * @return ChatClient
     */
    @Bean
    public ChatClient dashScopeMemoryClient(DashScopeChatModel model, ChatMemory chatMemory) {
        return ChatClient.builder(model)
                .defaultSystem(DEFAULT_SYSTEM)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        SimpleLoggerAdvisor.builder().build()
                )
                .build();
    }

    /**
     * DashScope 无状态 / 工具调用 client。无记忆, 仅日志, 工具按需挂载。
     *
     * @param model DashScope 模型
     * @return ChatClient
     */
    @Bean
    public ChatClient dashScopeToolClient(DashScopeChatModel model) {
        return ChatClient.builder(model)
                .defaultSystem(DEFAULT_SYSTEM)
                .defaultAdvisors(SimpleLoggerAdvisor.builder().build())
                // .defaultTools(...) // 需要工具调用时在此或调用处挂载
                .build();
    }

    /**
     * DashScope RAG 检索增强 client
     *
     * <p>
     * 仅当容器内存在 VectorStore bean 时才装配, 避免当前无 VectorStore 时启动失败
     *
     * @param model       DashScope 模型
     * @param vectorStore 向量库
     * @param chatMemory  对话记忆
     * @return ChatClient
     */
    @Bean
    @ConditionalOnBean(VectorStore.class)
    public ChatClient dashScopeRagClient(DashScopeChatModel model, VectorStore vectorStore, ChatMemory chatMemory) {
        return ChatClient.builder(model)
                .defaultSystem(DEFAULT_SYSTEM)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore).build(),
                        SimpleLoggerAdvisor.builder().build()
                )
                .build();
    }

    // ==================== Ollama ====================

    /**
     * Ollama 记忆对话 client
     *
     * @param model      Ollama 模型
     * @param chatMemory 对话记忆
     * @return ChatClient
     */
    @Bean
    public ChatClient ollamaMemoryClient(OllamaChatModel model, ChatMemory chatMemory) {
        return ChatClient.builder(model)
                .defaultSystem(DEFAULT_SYSTEM)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        SimpleLoggerAdvisor.builder().build()
                )
                .build();
    }

    /**
     * Ollama 无状态 / 工具调用 client
     *
     * @param model Ollama 模型
     * @return ChatClient
     */
    @Bean
    public ChatClient ollamaToolClient(OllamaChatModel model) {
        return ChatClient.builder(model)
                .defaultSystem(DEFAULT_SYSTEM)
                .defaultAdvisors(SimpleLoggerAdvisor.builder().build())
                .build();
    }

    // ==================== OpenAI (兼容 vLLM / TEI 等) ====================

    /**
     * OpenAI 记忆对话 client
     *
     * @param model      OpenAI 兼容模型 (vLLM / TEI 等)
     * @param chatMemory 对话记忆
     * @return ChatClient
     */
    @Bean
    public ChatClient openAiMemoryClient(OpenAiChatModel model, ChatMemory chatMemory) {
        return ChatClient.builder(model)
                .defaultSystem(DEFAULT_SYSTEM)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        SimpleLoggerAdvisor.builder().build()
                )
                .build();
    }

    /**
     * OpenAI 无状态 / 工具调用 client
     *
     * @param model OpenAI 兼容模型 (vLLM / TEI 等)
     * @return ChatClient
     */
    @Bean
    public ChatClient openAiToolClient(OpenAiChatModel model) {
        return ChatClient.builder(model)
                .defaultSystem(DEFAULT_SYSTEM)
                .defaultAdvisors(SimpleLoggerAdvisor.builder().build())
                .build();
    }

}
