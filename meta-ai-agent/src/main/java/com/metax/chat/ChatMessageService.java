package com.metax.chat;

import com.metax.chat.history.MetaChatHistoryType;
import com.metax.chat.request.ChatRequest;
import com.metax.chat.response.ChatMessageResponse;
import com.metax.chat.session.MetaChatDO;
import com.metax.chat.support.*;
import com.metax.rag.retrieval.advisor.MetaContextFile;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * ChatMessageService .
 *
 * <p>
 * 普通记忆对话和会话文件对话编排服务
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    /**
     * Spring AI 聊天客户端
     *
     * <p>
     * 当前服务只负责装配 prompt、Advisor 参数和用户消息，不直接处理底层模型配置
     */
    private final ChatClient chatClient;

    /**
     * 会话作用域解析器
     *
     * <p>
     * 统一处理 chatId 兜底生成，以及 tenantId / userId 与会话文件的绑定校验
     */
    private final ChatScopeResolver chatScopeResolver;

    /**
     * 聊天历史归档组件
     *
     * <p>
     * 负责维护会话主表和完整消息历史，和 Spring AI ChatMemory 的上下文窗口职责分离
     */
    private final ChatHistoryRecorder chatHistoryRecorder;

    /**
     * 流式事件组装器
     *
     * <p>
     * 负责把 ChatClient 流转换成前端统一消费的 meta、delta、done、error 事件
     */
    private final ChatStreamEventAssembler chatStreamEventAssembler;

    /**
     * 会话文件对话支持组件
     *
     * <p>
     * 负责已上传文件解析、历史 READY 文件回退，以及 MetaContextFileAdvisor 参数写入
     */
    private final ContextFileChatSupport contextFileChatSupport;

    /**
     * 执行普通记忆对话
     *
     * @param request 记忆对话请求参数
     * @return 记忆对话响应
     */
    public ChatMessageResponse chat(ChatRequest request) {
        String msg = messageOrDefault(request.getMsg(), ChatDefaults.CHAT_MESSAGE);
        // 非流式和流式共享 fileIds 解析规则，避免 GET / POST 双协议产生文件选择差异
        return chatWithFiles(request.getChatId(), request.getTenantId(), request.getUserId(), msg,
                request.getFileIds());
    }

    /**
     * 执行普通记忆流式对话
     *
     * @param request 记忆对话请求参数
     * @return SSE 流式事件
     */
    public Flux<ServerSentEvent<Object>> chatStream(ChatRequest request) {
        String msg = messageOrDefault(request.getMsg(), ChatDefaults.CHAT_MESSAGE);
        // 流式入口同样先解析会话文件，再把文件来源写入 done 事件返回给前端
        return streamChatWithFiles(request.getChatId(), request.getTenantId(), request.getUserId(), msg,
                request.getFileIds());
    }

    /**
     * 执行已上传文件 ID 非流式对话
     *
     * <p>
     * fileIds 为空时回退当前会话 READY 文件，非空时只使用显式指定文件
     *
     * @param chatId   会话 ID
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param msg      用户消息
     * @param fileIds  会话文件 ID 列表
     * @return 记忆对话响应
     */
    private ChatMessageResponse chatWithFiles(String chatId,
                                              String tenantId,
                                              String userId,
                                              String msg,
                                              List<String> fileIds) {
        // chatId 先兜底，后续 ChatMemory、历史归档和响应中的 chatId 都使用同一个值
        String resolvedChatId = chatScopeResolver.resolveChatId(chatId);
        ChatScope scope = chatScopeResolver.required(resolvedChatId, tenantId, userId);

        // fileIds 为空代表沿用当前会话 READY 文件，非空代表只使用用户显式选择的文件
        List<MetaContextFile> resolvedFiles = contextFileChatSupport.resolveReadyFiles(scope.tenantId(),
                scope.userId(), resolvedChatId, fileIds);
        MetaChatHistoryType historyType = resolvedFiles.isEmpty()
                ? MetaChatHistoryType.CHAT
                : MetaChatHistoryType.FILE_CHAT;

        // 完整历史归档走 MetaChatHistory，ChatMemory 只作为模型上下文窗口使用
        MetaChatDO chat = chatHistoryRecorder.getOrCreate(resolvedChatId, scope.tenantId(), scope.userId(),
                historyType, msg, null);

        chatHistoryRecorder.saveUserMessage(chat.getId(), resolvedChatId, historyType, msg);

        ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt();
        if (resolvedFiles.isEmpty()) {
            // 这里直接绑定 Spring AI 官方 conversationId，保持 MessageChatMemoryAdvisor 原生行为
            requestSpec.advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, resolvedChatId));
        } else {
            // 有文件上下文时通过 MetaContextFileAdvisor 检索 session scope 文件 chunk 并增强 prompt
            requestSpec.advisors(spec -> contextFileChatSupport.contextFileParams(spec, scope.tenantId(),
                    scope.userId(), resolvedChatId, msg, resolvedFiles));
        }
        String answer = requestSpec.user(msg).call().content();
        chatHistoryRecorder.saveAssistantMessage(chat.getId(), resolvedChatId, historyType, answer);
        // 非流式响应也返回本轮实际使用的文件，方便前端展示和排查 fileIds 是否生效
        return new ChatMessageResponse(answer, resolvedChatId, resolvedFiles);
    }

    /**
     * 执行已上传文件 ID 流式对话
     *
     * <p>
     * fileIds 为空时回退当前会话 READY 文件，非空时只使用显式指定文件
     *
     * @param chatId   会话 ID
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param msg      用户消息
     * @param fileIds  会话文件 ID 列表
     * @return SSE 流式事件
     */
    private Flux<ServerSentEvent<Object>> streamChatWithFiles(String chatId,
                                                              String tenantId,
                                                              String userId,
                                                              String msg,
                                                              List<String> fileIds) {
        // 流式链路在发送 meta 事件前就确定 chatId，避免前端和历史表出现两个会话标识
        String resolvedChatId = chatScopeResolver.resolveChatId(chatId);
        ChatScope scope = chatScopeResolver.required(resolvedChatId, tenantId, userId);

        // 与非流式保持同一套文件策略：空 fileIds 回退 READY 文件，非空只使用显式文件
        List<MetaContextFile> resolvedFiles = contextFileChatSupport.resolveReadyFiles(scope.tenantId(),
                scope.userId(), resolvedChatId, fileIds);
        MetaChatHistoryType historyType = resolvedFiles.isEmpty()
                ? MetaChatHistoryType.CHAT
                : MetaChatHistoryType.FILE_CHAT;

        MetaChatDO chat = chatHistoryRecorder.getOrCreate(resolvedChatId, scope.tenantId(), scope.userId(),
                historyType, msg, null);
        chatHistoryRecorder.saveUserMessage(chat.getId(), resolvedChatId, historyType, msg);

        // 流式 done 事件需要读取 Advisor 写入的 metadata，所以这里使用 chatClientResponseStream
        ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt()
                .advisors(spec -> contextFileChatSupport.contextFileParams(spec, scope.tenantId(), scope.userId(),
                        resolvedChatId, msg, resolvedFiles))
                .user(msg);
        return chatStreamEventAssembler.chatClientResponseStream(requestSpec, chat.getId(), resolvedChatId,
                historyType, false);
    }

    /**
     * 解析用户消息兜底值
     *
     * @param value        原始用户消息
     * @param defaultValue 默认消息
     * @return 实际发送给模型的消息
     */
    private String messageOrDefault(String value, String defaultValue) {
        // 控制器允许 msg 为空，服务层在进入 ChatClient 前统一补默认 prompt
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
