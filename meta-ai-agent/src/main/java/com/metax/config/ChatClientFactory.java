package com.metax.config;

import com.metax.prompt.PromptTemplateId;
import com.metax.prompt.PromptTemplates;
import com.metax.tool.foundation.DateTimeTools;
import lombok.RequiredArgsConstructor;
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
 * 基础 client 固定 Memory / Logger，并只允许无副作用基础工具作为全局默认工具
 * RAG 检索 Advisor 由调用侧按请求参数动态追加，便于覆盖 topK、similarityThreshold 和 filterExpression
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/29
 */
@Component
@RequiredArgsConstructor
public class ChatClientFactory {

    private final DateTimeTools dateTimeTools;

    /**
     * 构造基础记忆对话 client
     *
     * <p>
     * 基础链路包含 Memory、Logger 和无副作用当前时间工具，不包含 RAG / 业务 Tools
     * 当前时间工具通过 Spring AI defaultTools 全局挂载，用于参考验证官方默认工具路径
     * 该 client 绑定 CHAT_GENERAL_SYSTEM，适合普通多轮对话和上下文问答
     *
     * @param model      对话模型
     * @param chatMemory 对话记忆
     * @return ChatClient
     */
    public ChatClient buildChatClient(ChatModel model, ChatMemory chatMemory) {
        return buildClient(model, chatMemory, PromptTemplateId.CHAT_GENERAL_SYSTEM, true);
    }

    /**
     * 构造 RAG 检索增强对话 client
     *
     * <p>
     * RAG 链路同样固定 Memory、Logger 和无副作用当前时间工具，但绑定 RAG_RETRIEVAL_SYSTEM
     * RetrievalAugmentationAdvisor 仍由请求侧动态追加，避免把请求级 filter、topK 和 similarityThreshold 固化到 Bean
     *
     * @param model      对话模型
     * @param chatMemory 对话记忆
     * @return RAG ChatClient
     */
    public ChatClient buildRagChatClient(ChatModel model, ChatMemory chatMemory) {
        return buildClient(model, chatMemory, PromptTemplateId.RAG_RETRIEVAL_SYSTEM, true);
    }

    /**
     * 构造请求级工具对话 client
     *
     * <p>
     * 该 client 只固定 Memory 和 Logger，不挂载 defaultTools
     * 业务工具必须由调用侧通过 ToolCallAdvisor、toolCallbacks 和 toolContext 显式注入
     *
     * @param model      对话模型
     * @param chatMemory 对话记忆
     * @return 请求级工具 ChatClient
     */
    public ChatClient buildRequestToolChatClient(ChatModel model, ChatMemory chatMemory) {
        return buildClient(model, chatMemory, PromptTemplateId.CHAT_GENERAL_SYSTEM, false);
    }

    /**
     * 构造统一 ChatClient
     *
     * <p>
     * 该方法是普通对话、RAG 对话和请求级工具对话的共同构造入口
     * 统一固定系统提示词、Memory Advisor、Logger Advisor 和默认基础工具准入规则
     *
     * @param model                  对话模型
     * @param chatMemory             对话记忆
     * @param systemPrompt           系统提示词模板 ID
     * @param defaultFoundationTools 是否挂载默认安全基础工具
     * @return 预配置 ChatClient
     */
    private ChatClient buildClient(ChatModel model,
                                   ChatMemory chatMemory,
                                   PromptTemplateId systemPrompt,
                                   boolean defaultFoundationTools) {
        // 系统提示词在构造阶段绑定，保证同一个 ChatClient 的角色边界稳定
        ChatClient.Builder builder = ChatClient.builder(model)
                .defaultSystem(PromptTemplates.render(systemPrompt))
                // Advisor 顺序固定为 Memory -> Logger，避免各入口重复声明导致顺序漂移
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        SimpleLoggerAdvisor.builder().build()
                );
        if (defaultFoundationTools) {
            // defaultTools 对该 ChatClient 的所有请求可见，只允许挂载无副作用基础工具
            builder.defaultTools(dateTimeTools);
        }
        return builder.build();
    }
}
