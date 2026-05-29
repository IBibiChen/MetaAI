package com.metax.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClientConfig .
 *
 * <p>
 * 多套 ChatModel 共存后，框架的 ChatClient.Builder 自动装配已通过 spring.ai.chat.client.enabled=false 关闭
 * 故此处对每个具体 ChatModel 子类型用 ChatClient.builder(model) 显式构造预配置 ChatClient
 *
 * <p>
 * 三套接入按 "协议 (provider)" 划分，而非部署位置 —— 同一协议可云可本地 (如 OpenAI 协议既可接官方 API 也可接本地 vLLM)
 * 按 "模型 × 场景" 组合：DashScope / Ollama / OpenAI (兼容 vLLM / TEI 等) 各自派生 默认记忆对话 和 RAG 检索增强 client
 * 运行时的工具调用、特殊提示词或特殊模型参数由调用方注入具体 ChatModel 后用 ChatClient.builder(model) 临时构造
 *
 * <p>
 * 不预置 tool client：当前没有全局默认工具集，提前定义工具调用 client 会制造误导
 * 有工具调用需求时，应在具体业务场景中按需挂载 tools / toolCallbacks
 *
 * <p>
 * Advisor 顺序敏感：请求方向 Memory -> RAG -> Logger -> Model，SimpleLoggerAdvisor 置于链末端
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

    // ==================== DashScope ====================

    /**
     * DashScope 默认记忆对话 client
     *
     * <p>
     * 默认记忆对话场景，使用 DashScopeChatModel 生成回答，绑定 ChatMemory 保存会话上下文，并挂载 SimpleLoggerAdvisor 输出调试日志
     *
     * @param model      DashScope 模型 (starter 自动装配)
     * @param chatMemory 对话记忆
     * @return ChatClient
     */
    @Bean
    public ChatClient dashScopeChatClient(DashScopeChatModel model, ChatMemory chatMemory) {
        return buildDefaultClient(model, chatMemory);
    }

    /**
     * DashScope RAG 检索增强 client
     *
     * <p>
     * RAG 检索增强场景，使用 DashScopeChatModel 生成回答，绑定 dashScopeVectorStore 检索同协议 embedding 写入的知识库内容
     *
     * @param model       DashScope 模型 (starter 自动装配)
     * @param chatMemory  对话记忆
     * @param vectorStore DashScope 向量库
     * @return ChatClient
     */
    @Bean
    public ChatClient dashScopeRagChatClient(DashScopeChatModel model,
                                             ChatMemory chatMemory,
                                             @Qualifier("dashScopeVectorStore") VectorStore vectorStore) {
        return buildRagClient(model, chatMemory, vectorStore);
    }


    // ==================== Ollama ====================

    /**
     * Ollama 默认记忆对话 client
     *
     * <p>
     * 默认记忆对话场景，使用 OllamaChatModel 生成回答，绑定 ChatMemory 保存会话上下文，并挂载 SimpleLoggerAdvisor 输出调试日志
     *
     * @param model      Ollama 模型 (starter 自动装配)
     * @param chatMemory 对话记忆
     * @return ChatClient
     */
    @Bean
    public ChatClient ollamaChatClient(OllamaChatModel model, ChatMemory chatMemory) {
        return buildDefaultClient(model, chatMemory);
    }

    /**
     * Ollama RAG 检索增强 client
     *
     * <p>
     * RAG 检索增强场景，使用 OllamaChatModel 生成回答，绑定 ollamaVectorStore 检索同协议 embedding 写入的知识库内容
     *
     * @param model       Ollama 模型 (starter 自动装配)
     * @param chatMemory  对话记忆
     * @param vectorStore Ollama 向量库
     * @return ChatClient
     */
    @Bean
    public ChatClient ollamaRagChatClient(OllamaChatModel model,
                                          ChatMemory chatMemory,
                                          @Qualifier("ollamaVectorStore") VectorStore vectorStore) {
        return buildRagClient(model, chatMemory, vectorStore);
    }

    // ==================== OpenAI (兼容 vLLM / TEI 等) ====================

    /**
     * OpenAI 默认记忆对话 client
     *
     * <p>
     * 默认记忆对话场景，使用 OpenAiChatModel 生成回答，绑定 ChatMemory 保存会话上下文，并挂载 SimpleLoggerAdvisor 输出调试日志
     *
     * @param model      OpenAI 兼容模型 (vLLM / TEI 等, starter 自动装配)
     * @param chatMemory 对话记忆
     * @return ChatClient
     */
    @Bean
    public ChatClient openAiChatClient(OpenAiChatModel model, ChatMemory chatMemory) {
        return buildDefaultClient(model, chatMemory);
    }

    /**
     * OpenAI RAG 检索增强 client
     *
     * <p>
     * RAG 检索增强场景，使用 OpenAI 协议兼容模型生成回答，绑定 openAiVectorStore 检索同协议 embedding 写入的知识库内容
     *
     * @param model       OpenAI 兼容模型 (vLLM / TEI 等, starter 自动装配)
     * @param chatMemory  对话记忆
     * @param vectorStore OpenAI 向量库
     * @return ChatClient
     */
    @Bean
    public ChatClient openAiRagChatClient(OpenAiChatModel model,
                                          ChatMemory chatMemory,
                                          @Qualifier("openAiVectorStore") VectorStore vectorStore) {
        return buildRagClient(model, chatMemory, vectorStore);
    }

    /**
     * 构造默认记忆对话 client
     *
     * <p>
     * 默认链路只包含 Memory 和 Logger，不包含 RAG / Tools，避免普通对话被检索或工具调用副作用污染
     *
     * @param model      对话模型
     * @param chatMemory 对话记忆
     * @return ChatClient
     */
    private ChatClient buildDefaultClient(ChatModel model, ChatMemory chatMemory) {
        return ChatClient.builder(model)
                .defaultSystem(DEFAULT_SYSTEM)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        SimpleLoggerAdvisor.builder().build()
                )
                .build();
    }

    /**
     * 构造 RAG 检索增强 client
     *
     * <p>
     * Advisor 顺序固定为 Memory -> RAG -> Logger
     * RAG 检索应看到会话历史，Logger 应放在链路末端记录最终请求和响应
     *
     * @param model       对话模型
     * @param chatMemory  对话记忆
     * @param vectorStore 向量库
     * @return ChatClient
     */
    private ChatClient buildRagClient(ChatModel model, ChatMemory chatMemory, VectorStore vectorStore) {
        return ChatClient.builder(model)
                .defaultSystem(DEFAULT_SYSTEM)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore).build(),
                        SimpleLoggerAdvisor.builder().build()
                )
                .build();
    }

}
