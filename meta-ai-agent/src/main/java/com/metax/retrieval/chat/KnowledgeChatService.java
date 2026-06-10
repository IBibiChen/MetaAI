package com.metax.retrieval.chat;

import com.metax.chat.history.MetaChatHistoryType;
import com.metax.chat.session.MetaChatDO;
import com.metax.chat.support.*;
import com.metax.rag.retrieval.advisor.MetaContextFile;
import com.metax.rag.retrieval.advisor.RetrievalAdvisorFactory;
import com.metax.rag.retrieval.assembly.RetrievalResponseAssembler;
import com.metax.rag.retrieval.decision.RetrievalDecision;
import com.metax.rag.retrieval.decision.RetrievalDecisionResult;
import com.metax.rag.retrieval.decision.RetrievalDecisionService;
import com.metax.rag.retrieval.filter.RetrievalFilterExpressionFactory;
import com.metax.rag.retrieval.model.RetrievalChatResponse;
import com.metax.rag.retrieval.model.RetrievalOptions;
import com.metax.retrieval.chat.request.RetrievalChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;

/**
 * KnowledgeChatService .
 *
 * <p>
 * 知识库问答和知识库流式问答编排服务
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeChatService {

    private final ChatClient ragChatClient;

    private final ChatModel chatModel;

    private final VectorStore vectorStore;

    private final RetrievalAdvisorFactory retrievalAdvisorFactory;

    private final RetrievalFilterExpressionFactory retrievalFilterExpressionFactory;

    private final RetrievalResponseAssembler retrievalResponseAssembler;

    private final RetrievalDecisionService retrievalDecisionService;

    private final RetrievalOptionsFactory retrievalOptionsFactory;

    private final ChatScopeResolver chatScopeResolver;

    private final ChatHistoryRecorder chatHistoryRecorder;

    private final ChatStreamEventAssembler chatStreamEventAssembler;

    private final ContextFileChatSupport contextFileChatSupport;

    /**
     * 执行知识库问答
     *
     * @param request 知识库问答请求参数
     * @return 知识库问答响应
     */
    public RetrievalChatResponse chat(RetrievalChatRequest request) {
        String msg = messageOrDefault(request.getMsg(), ChatDefaults.CHAT_MESSAGE);
        // RAG 非流式也走 fileIds 解析，保证 GET / POST 与流式接口的文件行为一致
        return chatWithRetrieval(request.getChatId(), msg, retrievalOptionsFactory.create(request, msg),
                MetaChatHistoryType.RAG, request.getFileIds());
    }

    /**
     * 执行知识库流式问答
     *
     * @param request 知识库问答请求参数
     * @return SSE 流式事件
     */
    public Flux<ServerSentEvent<Object>> chatStream(RetrievalChatRequest request) {
        String msg = messageOrDefault(request.getMsg(), ChatDefaults.CHAT_MESSAGE);
        // 流式 RAG 使用深层响应流，done 事件需要同时组装 references 和 files
        return streamChatWithRetrieval(request.getChatId(), msg, retrievalOptionsFactory.create(request, msg),
                MetaChatHistoryType.RAG, request.getFileIds());
    }

    /**
     * 执行知识库非流式对话
     *
     * <p>
     * 检索决策为 SKIP 时仍然保留会话文件上下文 Advisor，支持“只基于上传文件回答”的场景
     * 检索决策为 RETRIEVE 时同时注入会话文件上下文和知识库 Advisor
     *
     * @param chatId      会话 ID
     * @param msg         用户消息
     * @param options     检索参数
     * @param historyType 历史类型
     * @param fileIds     会话文件 ID 列表
     * @return 知识库问答响应
     */
    private RetrievalChatResponse chatWithRetrieval(String chatId,
                                                    String msg,
                                                    RetrievalOptions options,
                                                    MetaChatHistoryType historyType,
                                                    List<String> fileIds) {
        // 所有知识库问答先统一会话 ID，保证 ChatMemory、完整历史和流式 meta 使用同一标识
        String resolvedChatId = chatScopeResolver.resolveChatId(chatId);
        RetrievalOptions resolvedOptions = Objects.requireNonNull(options, "RetrievalOptions must not be null");
        // 先把 HTTP 层 fileIds 解析成领域对象，后续 RAG 编排只处理 MetaContextFile
        List<MetaContextFile> files = resolveContextFiles(resolvedChatId, resolvedOptions, fileIds);

        // 先创建会话主表并保存用户消息，后续不论是否检索都能完整回放本轮问答
        MetaChatDO chat = chatHistoryRecorder.getOrCreate(resolvedChatId, resolvedOptions.getTenantId(),
                resolvedOptions.getUserId(), historyType, msg, resolvedOptions.getKbId());

        chatHistoryRecorder.saveUserMessage(chat, historyType, msg);

        // 检索决策用于短路明显不需要知识库的请求，避免无意义召回和上下文污染
        RetrievalDecisionResult decision = retrievalDecisionService.decide(resolvedOptions);
        log.info("RAG 检索决策：chatId = {}，decision = {}，reason = {}，query = {}",
                resolvedChatId, decision.decision(), decision.reason(), resolvedOptions.getQuery());
        RetrievalChatResponse response;
        ChatClient.ChatClientRequestSpec requestSpec = ragChatClient.prompt()
                // RAG ChatClient 默认挂载 MessageChatMemoryAdvisor，所有分支都必须先绑定 conversationId
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, resolvedChatId));
        if (decision.decision() == RetrievalDecision.SKIP) {
            // SKIP 时不注入知识库 Advisor，但保留文件上下文以支持上传文件问答
            if (files.isEmpty()) {
                // 没有知识库检索也没有会话文件时，退化为 RAG ChatClient 的普通记忆回答
                response = retrievalResponseAssembler.chatWithoutReferences(requestSpec
                        .user(msg)
                        .call()
                        .chatClientResponse(), resolvedChatId);
            } else {
                // 用户可能只想问上传文件，检索决策 SKIP 不能跳过文件上下文
                response = retrievalResponseAssembler.chatWithoutReferences(requestSpec
                        .advisors(spec -> contextFileChatSupport.contextFileParams(spec,
                                resolvedOptions.getTenantId(), resolvedOptions.getUserId(), resolvedChatId,
                                msg, files))
                        .user(msg)
                        .call()
                        .chatClientResponse(), resolvedChatId);
            }
        } else {
            // RETRIEVE 时同时启用文件上下文和知识库检索，普通响应返回 references 和 files
            response = retrievalResponseAssembler.chat(requestSpec
                    .advisors(spec -> {
                        if (!files.isEmpty()) {
                            // 会话文件只进入 files 字段，不混入知识库 references
                            contextFileChatSupport.contextFileParams(spec, resolvedOptions.getTenantId(),
                                    resolvedOptions.getUserId(), resolvedChatId, msg, files);
                        }
                        spec.advisors(retrievalAdvisorFactory.create(vectorStore, chatModel, resolvedOptions,
                                retrievalFilterExpressionFactory.create(resolvedOptions)));
                    })
                    .user(msg)
                    .call()
                    .chatClientResponse(), resolvedChatId);
        }

        // 助手完整回答和知识库 references 一起归档，前端历史页不依赖 ChatMemory
        chatHistoryRecorder.saveAssistantMessage(chat, historyType, response.answer(), response.references());
        return response;
    }

    /**
     * 执行知识库流式对话
     *
     * <p>
     * 检索决策为 SKIP 时仍然保留会话文件上下文 Advisor，支持“只基于上传文件回答”的场景
     * 检索决策为 RETRIEVE 时同时注入会话文件上下文和知识库 Advisor
     *
     * @param chatId      会话 ID
     * @param msg         用户消息
     * @param options     检索参数
     * @param historyType 历史类型
     * @param fileIds     会话文件 ID 列表
     * @return SSE 流式事件
     */
    private Flux<ServerSentEvent<Object>> streamChatWithRetrieval(String chatId,
                                                                  String msg,
                                                                  RetrievalOptions options,
                                                                  MetaChatHistoryType historyType,
                                                                  List<String> fileIds) {
        // 流式链路同样先固定 chatId，确保 meta、ChatMemory 和历史归档一致
        String resolvedChatId = chatScopeResolver.resolveChatId(chatId);
        RetrievalOptions resolvedOptions = Objects.requireNonNull(options, "RetrievalOptions must not be null");
        // 流式链路和非流式链路共用 fileIds 解析规则，避免同一请求得到不同文件集合
        List<MetaContextFile> files = resolveContextFiles(resolvedChatId, resolvedOptions, fileIds);

        // 流式响应开始前保存用户消息，助手消息在 done 事件阶段统一保存完整文本
        MetaChatDO chat = chatHistoryRecorder.getOrCreate(resolvedChatId, resolvedOptions.getTenantId(),
                resolvedOptions.getUserId(), historyType, msg, resolvedOptions.getKbId());

        chatHistoryRecorder.saveUserMessage(chat, historyType, msg);

        // 先决策再组装 Advisor，避免 SKIP 场景误把知识库上下文塞进 prompt
        RetrievalDecisionResult decision = retrievalDecisionService.decide(resolvedOptions);
        log.info("RAG 检索决策：chatId = {}，decision = {}，reason = {}，query = {}",
                resolvedChatId, decision.decision(), decision.reason(), resolvedOptions.getQuery());
        ChatClient.ChatClientRequestSpec requestSpec = ragChatClient.prompt()
                // 流式 SKIP 且没有文件时不会进入 contextFileParams，这里统一兜住 ChatMemory conversationId
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, resolvedChatId));
        if (decision.decision() == RetrievalDecision.SKIP) {
            // SKIP 只启用会话文件上下文和 ChatMemory，不返回知识库 references
            if (!files.isEmpty()) {
                // 有上传文件时仍走深层响应流，done 事件才能拿到 files 元数据
                requestSpec.advisors(spec -> contextFileChatSupport.contextFileParams(spec,
                        resolvedOptions.getTenantId(), resolvedOptions.getUserId(), resolvedChatId, msg, files));
            }
        } else {
            // RETRIEVE 同时启用文件上下文和知识库检索，done 事件需要从 ChatClientResponse 组装 references
            requestSpec.advisors(spec -> {
                // 文件上下文和知识库上下文独立写入 metadata，响应组装阶段分别生成 files 和 references
                contextFileChatSupport.contextFileParams(spec, resolvedOptions.getTenantId(),
                        resolvedOptions.getUserId(), resolvedChatId, msg, files);
                spec.advisors(retrievalAdvisorFactory.create(vectorStore, chatModel, resolvedOptions,
                        retrievalFilterExpressionFactory.create(resolvedOptions)));
            });
        }
        requestSpec.user(msg);

        // includeReferences 控制 done 事件是否读取检索 metadata 并归档 references
        return chatStreamEventAssembler.chatClientResponseStream(requestSpec, chat, historyType,
                decision.decision() == RetrievalDecision.RETRIEVE);
    }

    /**
     * 解析 RAG 本轮会话文件上下文
     *
     * <p>
     * fileIds 为空时回退当前会话 READY 文件，非空时只返回显式指定文件
     *
     * @param chatId  会话 ID
     * @param options 检索参数
     * @param fileIds 会话文件 ID 列表
     * @return 本轮参与上下文增强的会话文件
     */
    private List<MetaContextFile> resolveContextFiles(String chatId, RetrievalOptions options, List<String> fileIds) {
        // RAG 文件上下文同样受 tenantId、userId、chatId 三重隔离约束
        String resolvedChatId = chatScopeResolver.resolveChatId(chatId);
        RetrievalOptions resolvedOptions = Objects.requireNonNull(options, "RetrievalOptions must not be null");
        return contextFileChatSupport.resolveReadyFiles(resolvedOptions.getTenantId(), resolvedOptions.getUserId(),
                resolvedChatId, fileIds);
    }

    /**
     * 解析用户消息兜底值
     *
     * @param value        原始用户消息
     * @param defaultValue 默认消息
     * @return 实际发送给模型的消息
     */
    private String messageOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
