package com.metax.config;

import com.metax.prompt.PromptTemplateId;
import com.metax.prompt.PromptTemplates;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * ChatClientFactory .
 *
 * <p>
 * 统一构造预配置 ChatClient，集中维护默认系统提示词和 Advisor 顺序
 * Chat client 和 RAG chat client 分别绑定不同系统提示词
 * 基础 client 只固定 Memory / Logger
 * RAG 检索 Advisor 由调用侧按请求参数动态追加，便于覆盖 topK、similarityThreshold 和 filterExpression
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/29
 */
@Component
public class ChatClientFactory {

    /**
     * 构造基础记忆对话 client
     *
     * <p>
     * 基础链路只包含 Memory 和 Logger，不包含 RAG / Tools，避免普通对话被检索或工具调用副作用污染
     * 该 client 绑定 CHAT_GENERAL_SYSTEM，适合普通多轮对话和上下文问答
     *
     * @param model      对话模型
     * @param chatMemory 对话记忆
     * @return ChatClient
     */
    public ChatClient buildChatClient(ChatModel model, ChatMemory chatMemory) {
        return buildClient(model, chatMemory, PromptTemplateId.CHAT_GENERAL_SYSTEM);
    }

    /**
     * 构造 RAG 检索增强对话 client
     *
     * <p>
     * RAG 链路同样固定 Memory 和 Logger，但绑定 RAG_RETRIEVAL_SYSTEM
     * RetrievalAugmentationAdvisor 仍由请求侧动态追加，避免把请求级 filter、topK 和 similarityThreshold 固化到 Bean
     *
     * @param model      对话模型
     * @param chatMemory 对话记忆
     * @return RAG ChatClient
     */
    public ChatClient buildRagChatClient(ChatModel model, ChatMemory chatMemory) {
        return buildClient(model, chatMemory, PromptTemplateId.RAG_RETRIEVAL_SYSTEM);
    }

    private ChatClient buildClient(ChatModel model, ChatMemory chatMemory, PromptTemplateId systemPrompt) {
        return ChatClient.builder(model)
                .defaultSystem(PromptTemplates.render(systemPrompt))
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        SimpleLoggerAdvisor.builder().build()
                )
                .build();
    }
}
