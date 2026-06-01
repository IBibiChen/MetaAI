package com.metax.config;

import com.metax.prompt.PromptTemplateId;
import com.metax.prompt.PromptTemplates;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

/**
 * ChatClientFactory .
 *
 * <p>
 * 统一构造预配置 ChatClient，集中维护默认系统提示词和 Advisor 顺序
 * 默认对话只挂载 Memory / Logger，RAG 基础 client 也只固定 Memory / Logger
 * RAG 检索 Advisor 由调用侧按请求参数动态追加，便于覆盖 topK、similarityThreshold 和 filterExpression
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/29
 */
@Component
public class ChatClientFactory {

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
                .defaultSystem(PromptTemplates.render(PromptTemplateId.CHAT_GENERAL_SYSTEM))
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
     * 这里只构造 RAG 基础 client，固定 Memory -> Logger
     * 真正的 RAG Advisor 在 Controller 中按请求级 topK、similarityThreshold、filterExpression 动态追加
     *
     * @param model       对话模型
     * @param chatMemory  对话记忆
     * @param vectorStore 向量库绑定占位，用于保持配置类 Bean 关系清晰
     * @return ChatClient
     */
    public ChatClient buildRagClient(ChatModel model, ChatMemory chatMemory, VectorStore vectorStore) {
        return ChatClient.builder(model)
                .defaultSystem(PromptTemplates.render(PromptTemplateId.RAG_RETRIEVAL_SYSTEM))
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        SimpleLoggerAdvisor.builder().build()
                )
                .build();
    }
}
