package com.metax.chat;

import com.metax.chat.file.MetaChatFileResponse;
import com.metax.chat.history.MetaChatHistoryType;
import com.metax.chat.request.ChatFileRequest;
import com.metax.chat.request.ChatRequest;
import com.metax.chat.support.*;
import com.metax.rag.retrieval.advisor.MetaContextFile;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
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
     * 负责本轮文件上传、历史 READY 文件回退，以及 MetaContextFileAdvisor 参数写入
     */
    private final ContextFileChatSupport contextFileChatSupport;

    /**
     * 执行普通记忆对话
     *
     * @param request 记忆对话请求参数
     * @return 模型响应内容
     */
    public String chat(ChatRequest request) {
        String msg = messageOrDefault(request.getMsg(), ChatDefaults.CHAT_MESSAGE);
        return memoryChat(request.getChatId(), request.getTenantId(), request.getUserId(), msg,
                MetaChatHistoryType.CHAT);
    }

    /**
     * 执行会话文件对话
     *
     * @param request 记忆对话文件请求参数
     * @return 文件上下文对话响应
     */
    public MetaChatFileResponse chatWithFiles(ChatFileRequest request) {
        String msg = messageOrDefault(request.getMsg(), ChatDefaults.FILE_CHAT_MESSAGE);
        return fileChat(request.getChatId(), request.getTenantId(), request.getUserId(), msg, request.getFiles());
    }

    /**
     * 执行普通记忆流式对话
     *
     * @param request 记忆对话请求参数
     * @return SSE 流式事件
     */
    public Flux<ServerSentEvent<Object>> chatStream(ChatRequest request) {
        String msg = messageOrDefault(request.getMsg(), ChatDefaults.CHAT_MESSAGE);

        // 先确定 chatId 并创建会话主表，SSE meta 事件和后续历史归档都复用同一个会话 ID
        String resolvedChatId = chatScopeResolver.resolveChatId(request.getChatId());
        MetaChatDO chat = chatHistoryRecorder.getOrCreate(resolvedChatId, request.getTenantId(), request.getUserId(),
                MetaChatHistoryType.CHAT, msg, null);
        chatHistoryRecorder.saveUserMessage(chat.getId(), resolvedChatId, MetaChatHistoryType.CHAT, msg);

        // MessageChatMemoryAdvisor 使用 Spring AI 官方 conversationId 参数名，这里显式绑定当前 chatId
        ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt()
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, resolvedChatId))
                .user(msg);
        return chatStreamEventAssembler.contentStream(requestSpec, chat.getId(), resolvedChatId,
                MetaChatHistoryType.CHAT);
    }

    /**
     * 执行会话文件流式对话
     *
     * @param request 记忆对话文件请求参数
     * @return SSE 流式事件
     */
    public Flux<ServerSentEvent<Object>> chatStreamWithFiles(ChatFileRequest request) {
        String msg = messageOrDefault(request.getMsg(), ChatDefaults.FILE_CHAT_MESSAGE);
        return fileStreamChat(request.getChatId(), request.getTenantId(), request.getUserId(), msg,
                request.getFiles());
    }

    /**
     * 执行基础记忆对话并归档完整历史
     *
     * @param chatId      会话 ID
     * @param tenantId    租户 ID
     * @param userId      用户 ID
     * @param msg         用户消息
     * @param historyType 历史类型
     * @return 模型响应内容
     */
    private String memoryChat(String chatId,
                              String tenantId,
                              String userId,
                              String msg,
                              MetaChatHistoryType historyType) {
        String resolvedChatId = chatScopeResolver.resolveChatId(chatId);

        // 完整历史归档走 MetaChatHistory，ChatMemory 只作为模型上下文窗口使用
        MetaChatDO chat = chatHistoryRecorder.getOrCreate(resolvedChatId, tenantId, userId, historyType, msg, null);

        chatHistoryRecorder.saveUserMessage(chat.getId(), resolvedChatId, historyType, msg);

        // 这里直接绑定 Spring AI 官方 conversationId，保持 MessageChatMemoryAdvisor 原生行为
        String answer = chatClient.prompt()
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, resolvedChatId))
                .user(msg)
                .call()
                .content();
        chatHistoryRecorder.saveAssistantMessage(chat.getId(), resolvedChatId, historyType, answer);
        return answer;
    }

    /**
     * 执行会话文件普通对话
     *
     * <p>
     * 有上传文件或历史 READY 文件时走 FILE_CHAT 历史类型，并通过 MetaContextFileAdvisor 注入文件上下文
     * 没有任何可用文件时退回普通记忆对话，避免无文件请求被错误归档为文件对话
     *
     * @param chatId   会话 ID
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param msg      用户消息
     * @param files    本轮上传文件
     * @return 文件对话响应
     */
    private MetaChatFileResponse fileChat(String chatId,
                                          String tenantId,
                                          String userId,
                                          String msg,
                                          org.springframework.web.multipart.MultipartFile[] files) {
        String resolvedChatId = chatScopeResolver.resolveChatId(chatId);
        ChatScope scope = chatScopeResolver.required(resolvedChatId, tenantId, userId);

        // 文件对话先上传本轮文件，本轮无上传时再回退到当前会话历史 READY 文件
        ContextFileChatSupport.ContextFiles resolvedFiles = contextFileChatSupport.uploadAndResolveContextFiles(
                scope.tenantId(), scope.userId(), resolvedChatId, files);

        // 没有任何可用文件时不创建 FILE_CHAT 历史，直接退回普通记忆对话
        if (resolvedFiles.contextFiles().isEmpty()) {
            String answer = memoryChat(resolvedChatId, scope.tenantId(), scope.userId(), msg,
                    MetaChatHistoryType.CHAT);
            return new MetaChatFileResponse(answer, resolvedChatId, List.of());
        }

        // 有文件上下文时走 FILE_CHAT，并由 MetaContextFileAdvisor 在 ChatClient 调用前注入文件片段
        MetaChatDO chat = chatHistoryRecorder.getOrCreate(resolvedChatId, scope.tenantId(), scope.userId(),
                MetaChatHistoryType.FILE_CHAT, msg, null);
        chatHistoryRecorder.saveUserMessage(chat.getId(), resolvedChatId, MetaChatHistoryType.FILE_CHAT, msg);
        ChatClientResponse response = chatClient.prompt()
                .advisors(spec -> contextFileChatSupport.contextFileParams(spec, scope.tenantId(), scope.userId(),
                        resolvedChatId, msg, resolvedFiles.uploaded()))
                .user(msg)
                .call()
                .chatClientResponse();
        String answer = content(response);
        chatHistoryRecorder.saveAssistantMessage(chat.getId(), resolvedChatId, MetaChatHistoryType.FILE_CHAT,
                answer);
        return new MetaChatFileResponse(answer, resolvedChatId, resolvedFiles.contextFiles());
    }

    /**
     * 执行会话文件流式对话
     *
     * <p>
     * 流式文件对话需要读取 ChatClientResponse metadata 中的 CONTEXT_FILES
     * 因此这里复用 chatClientResponseStream，而不是只消费 stream().content()
     *
     * @param chatId   会话 ID
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param msg      用户消息
     * @param files    本轮上传文件
     * @return SSE 流式事件
     */
    private Flux<ServerSentEvent<Object>> fileStreamChat(String chatId,
                                                         String tenantId,
                                                         String userId,
                                                         String msg,
                                                         org.springframework.web.multipart.MultipartFile[] files) {
        String resolvedChatId = chatScopeResolver.resolveChatId(chatId);
        ChatScope scope = chatScopeResolver.required(resolvedChatId, tenantId, userId);

        // 流式文件对话只上传本轮文件，历史 READY 文件由 MetaContextFileAdvisor 在运行时按会话回退
        List<MetaContextFile> uploaded = contextFileChatSupport.uploadFiles(scope.tenantId(), scope.userId(),
                resolvedChatId, files);

        // 先写入用户消息，再启动流式调用，done 事件中统一保存助手完整回答
        MetaChatDO chat = chatHistoryRecorder.getOrCreate(resolvedChatId, scope.tenantId(), scope.userId(),
                MetaChatHistoryType.FILE_CHAT, msg, null);
        chatHistoryRecorder.saveUserMessage(chat.getId(), resolvedChatId, MetaChatHistoryType.FILE_CHAT, msg);

        // 使用 chatClientResponseStream 保留 Advisor 写入的文件上下文 metadata，done 事件需要返回 files
        ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt()
                .advisors(spec -> contextFileChatSupport.contextFileParams(spec, scope.tenantId(), scope.userId(),
                        resolvedChatId, msg, uploaded))
                .user(msg);
        return chatStreamEventAssembler.chatClientResponseStream(requestSpec, chat.getId(), resolvedChatId,
                MetaChatHistoryType.FILE_CHAT, false);
    }

    /**
     * 提取非流式助手回答文本
     *
     * @param response Spring AI 深层响应对象
     * @return 助手回答文本，空响应返回 null
     */
    private String content(ChatClientResponse response) {
        // call().chatClientResponse() 在异常或空结果场景可能没有 result，调用方按 null 归档
        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse == null || chatResponse.getResult() == null) {
            return null;
        }
        AssistantMessage output = chatResponse.getResult().getOutput();
        return output == null ? null : output.getText();
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
