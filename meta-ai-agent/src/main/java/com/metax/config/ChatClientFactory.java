package com.metax.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

/**
 * ChatClientFactory .
 *
 * <p>
 * 统一构造预配置 ChatClient，集中维护默认系统提示词和 Advisor 顺序
 * 默认对话只挂载 Memory / Logger，RAG 对话固定使用 Memory -> RAG -> Logger
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/29
 */
@Component
public class ChatClientFactory {

    /**
     * 全局默认系统提示词
     */
    private static final String DEFAULT_SYSTEM = """
            你是一个专业、严谨、友好、幽默的智能助手。
            回答问题时请优先使用中文，请用海盗的口吻回答问题。
            如果不确定，请明确说明不确定，不要编造。
            """;

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
    public ChatClient buildDefaultClient(ChatModel model, ChatMemory chatMemory) {
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
    public ChatClient buildRagClient(ChatModel model, ChatMemory chatMemory, VectorStore vectorStore) {
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
