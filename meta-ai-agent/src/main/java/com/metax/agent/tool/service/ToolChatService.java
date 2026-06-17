package com.metax.agent.tool.service;

import com.metax.agent.tool.model.ToolChatRequest;
import com.metax.agent.tool.model.ToolChatResponse;
import com.metax.agent.tool.registry.MetaToolRegistry;
import com.metax.chat.history.MetaChatHistoryType;
import com.metax.chat.session.MetaChatDO;
import com.metax.chat.support.ChatDefaults;
import com.metax.chat.support.ChatHistoryRecorder;
import com.metax.chat.support.ChatScope;
import com.metax.chat.support.ChatScopeResolver;
import com.metax.tool.context.MetaToolContextKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * ToolChatService .
 *
 * <p>
 * 请求级显式工具对话编排服务，演示 Spring AI ToolCallAdvisor、ToolCallback 和 ToolContext 推荐组合
 * 该服务依赖 Lombok 生成构造器完成 Bean 注入
 * toolChatClient 通过字段名对齐 @Bean 方法名固定绑定到不挂载 defaultTools 的请求级工具 ChatClient
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
@Service
@RequiredArgsConstructor
public class ToolChatService {

    private final ChatClient toolChatClient;

    private final ToolCallAdvisor metaToolCallAdvisor;

    private final MetaToolRegistry metaToolRegistry;

    private final ChatScopeResolver chatScopeResolver;

    private final ChatHistoryRecorder chatHistoryRecorder;

    /**
     * 执行请求级显式工具对话
     *
     * <p>
     * 本方法只在本轮请求中暴露 allowlist 工具，并通过 ToolContext 传递租户、用户和会话边界
     * 工具调用结果不进入知识库 references，避免和 RAG 来源混淆
     *
     * @param request 请求级显式工具对话请求
     * @return 请求级显式工具对话响应
     */
    public ToolChatResponse chat(ToolChatRequest request) {
        String msg = messageOrDefault(request.getMsg(), ChatDefaults.CHAT_MESSAGE);
        String resolvedChatId = chatScopeResolver.resolveChatId(request.getChatId());
        ChatScope scope = chatScopeResolver.required(resolvedChatId, request.getTenantId(), request.getUserId());
        List<ToolCallback> toolCallbacks = metaToolRegistry.resolve(request.getToolNames());
        List<String> resolvedToolNames = toolCallbacks.stream()
                .map(callback -> callback.getToolDefinition().name())
                .toList();

        MetaChatDO chat = chatHistoryRecorder.getOrCreate(resolvedChatId, scope.tenantId(), scope.userId(),
                MetaChatHistoryType.CHAT, msg, null);
        chatHistoryRecorder.saveUserMessage(chat, MetaChatHistoryType.CHAT, msg);

        // 请求级工具链路显式绑定 conversationId、ToolCallAdvisor、ToolCallback 和 ToolContext
        String answer = toolChatClient.prompt()
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, resolvedChatId))
                .advisors(metaToolCallAdvisor)
                .toolCallbacks(toolCallbacks)
                .toolContext(toolContext(scope, resolvedChatId))
                .user(msg)
                .call()
                .content();
        chatHistoryRecorder.saveAssistantMessage(chat, MetaChatHistoryType.CHAT, answer);
        return new ToolChatResponse(answer, resolvedChatId, resolvedToolNames);
    }

    /**
     * 组装请求级工具上下文
     *
     * <p>
     * ToolContext 不会发送给模型，只在工具执行时提供租户、用户和会话边界
     *
     * @param scope  聊天作用域
     * @param chatId 会话 ID
     * @return Spring AI ToolContext 参数
     */
    private Map<String, Object> toolContext(ChatScope scope, String chatId) {
        return Map.of(
                MetaToolContextKeys.TENANT_ID, scope.tenantId(),
                MetaToolContextKeys.USER_ID, scope.userId(),
                MetaToolContextKeys.CHAT_ID, chatId
        );
    }

    /**
     * 解析用户消息兜底值
     *
     * @param value        原始用户消息
     * @param defaultValue 默认消息
     * @return 实际发送给模型的消息
     */
    private String messageOrDefault(String value, String defaultValue) {
        // Controller 允许 msg 为空，服务层在进入 ChatClient 前统一补默认 prompt
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
