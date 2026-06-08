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
import com.metax.retrieval.chat.request.RetrievalChatFileRequest;
import com.metax.retrieval.chat.request.RetrievalChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
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
@Service
@RequiredArgsConstructor
@Slf4j
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
        return ragChat(request.getChatId(), msg, retrievalOptionsFactory.create(request, msg),
                MetaChatHistoryType.RAG);
    }

    /**
     * 执行带会话文件的知识库问答
     *
     * @param request 知识库问答文件请求参数
     * @return 知识库问答响应
     */
    public RetrievalChatResponse chatWithFiles(RetrievalChatFileRequest request) {
        String msg = messageOrDefault(request.getMsg(), ChatDefaults.RETRIEVAL_FILE_CHAT_MESSAGE);
        return ragChat(request.getChatId(), msg, retrievalOptionsFactory.create(request, msg),
                MetaChatHistoryType.RAG, request.getFiles());
    }

    /**
     * 执行知识库流式问答
     *
     * @param request 知识库问答请求参数
     * @return SSE 流式事件
     */
    public Flux<ServerSentEvent<Object>> chatStream(RetrievalChatRequest request) {
        String msg = messageOrDefault(request.getMsg(), ChatDefaults.CHAT_MESSAGE);
        return ragStreamChat(request.getChatId(), msg, retrievalOptionsFactory.create(request, msg),
                MetaChatHistoryType.RAG);
    }

    /**
     * 执行带会话文件的知识库流式问答
     *
     * @param request 知识库问答文件请求参数
     * @return SSE 流式事件
     */
    public Flux<ServerSentEvent<Object>> chatStreamWithFiles(RetrievalChatFileRequest request) {
        String msg = messageOrDefault(request.getMsg(), ChatDefaults.RETRIEVAL_FILE_CHAT_MESSAGE);
        return ragStreamChat(request.getChatId(), msg, retrievalOptionsFactory.create(request, msg),
                MetaChatHistoryType.RAG, request.getFiles());
    }

    private RetrievalChatResponse ragChat(String chatId,
                                          String msg,
                                          RetrievalOptions options,
                                          MetaChatHistoryType historyType) {
        return ragChat(chatId, msg, options, historyType, null);
    }

    /**
     * 执行知识库普通对话
     *
     * <p>
     * kbId 是接口、业务层和向量 metadata filter 统一使用的知识库边界字段
     * files 只进入 scope = session 的会话文件上下文，不会写入知识库 references
     *
     * @param chatId      会话 ID
     * @param msg         用户消息
     * @param options     检索参数
     * @param historyType 历史类型
     * @param files       本轮上传文件
     * @return 知识库对话响应
     */
    private RetrievalChatResponse ragChat(String chatId,
                                          String msg,
                                          RetrievalOptions options,
                                          MetaChatHistoryType historyType,
                                          MultipartFile[] files) {
        // 所有知识库问答先统一会话 ID，保证 ChatMemory、完整历史和流式 meta 使用同一标识
        String resolvedChatId = chatScopeResolver.resolveChatId(chatId);
        RetrievalOptions resolvedOptions = Objects.requireNonNull(options, "RetrievalOptions must not be null");

        // 会话文件只作为 session scope 临时上下文，不进入知识库索引和 references
        List<MetaContextFile> uploaded = contextFileChatSupport.uploadFiles(resolvedOptions.getTenantId(),
                resolvedOptions.getUserId(), resolvedChatId, files);

        // 先创建会话主表并保存用户消息，后续不论是否检索都能完整回放本轮问答
        MetaChatDO chat = chatHistoryRecorder.getOrCreate(resolvedChatId, resolvedOptions.getTenantId(),
                resolvedOptions.getUserId(), historyType, msg, resolvedOptions.getKbId());

        chatHistoryRecorder.saveUserMessage(chat.getId(), resolvedChatId, historyType, msg);

        // 检索决策用于短路明显不需要知识库的请求，避免无意义召回和上下文污染
        RetrievalDecisionResult decision = retrievalDecisionService.decide(resolvedOptions);
        log.info("RAG 检索决策：chatId = {}，decision = {}，reason = {}，query = {}",
                resolvedChatId, decision.decision(), decision.reason(), resolvedOptions.getQuery());
        RetrievalChatResponse response;
        if (decision.decision() == RetrievalDecision.SKIP) {
            // SKIP 时仍保留会话文件 Advisor，支持用户只基于上传文件追问
            response = retrievalResponseAssembler.chatWithoutReferences(ragChatClient.prompt()
                    .advisors(spec -> contextFileChatSupport.contextFileParams(spec, resolvedOptions.getTenantId(),
                            resolvedOptions.getUserId(), resolvedChatId, msg, uploaded))
                    .user(msg)
                    .call()
                    .chatClientResponse(), resolvedChatId);
        } else {
            // RETRIEVE 时先注入会话文件上下文，再追加知识库检索 Advisor
            response = retrievalResponseAssembler.chat(ragChatClient.prompt()
                    .advisors(spec -> {
                        contextFileChatSupport.contextFileParams(spec, resolvedOptions.getTenantId(),
                                resolvedOptions.getUserId(), resolvedChatId, msg, uploaded);
                        spec.advisors(retrievalAdvisorFactory.create(vectorStore, chatModel, resolvedOptions,
                                retrievalFilterExpressionFactory.create(resolvedOptions)));
                    })
                    .user(msg)
                    .call()
                    .chatClientResponse(), resolvedChatId);
        }

        // 助手完整回答和知识库 references 一起归档，前端历史页不依赖 ChatMemory
        chatHistoryRecorder.saveAssistantMessage(chat.getId(), resolvedChatId, historyType, response.answer(),
                response.references());
        return response;
    }

    private Flux<ServerSentEvent<Object>> ragStreamChat(String chatId,
                                                        String msg,
                                                        RetrievalOptions options,
                                                        MetaChatHistoryType historyType) {
        return ragStreamChat(chatId, msg, options, historyType, null);
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
     * @param files       本轮上传文件
     * @return SSE 流式事件
     */
    private Flux<ServerSentEvent<Object>> ragStreamChat(String chatId,
                                                        String msg,
                                                        RetrievalOptions options,
                                                        MetaChatHistoryType historyType,
                                                        MultipartFile[] files) {
        // 流式链路同样先固定 chatId，确保 meta、ChatMemory 和历史归档一致
        String resolvedChatId = chatScopeResolver.resolveChatId(chatId);
        RetrievalOptions resolvedOptions = Objects.requireNonNull(options, "RetrievalOptions must not be null");

        // 本轮上传文件传给文件 Advisor，历史 READY 文件由 Advisor 在运行时按会话回退
        List<MetaContextFile> uploaded = contextFileChatSupport.uploadFiles(resolvedOptions.getTenantId(),
                resolvedOptions.getUserId(), resolvedChatId, files);

        // 流式响应开始前保存用户消息，助手消息在 done 事件阶段统一保存完整文本
        MetaChatDO chat = chatHistoryRecorder.getOrCreate(resolvedChatId, resolvedOptions.getTenantId(),
                resolvedOptions.getUserId(), historyType, msg, resolvedOptions.getKbId());

        chatHistoryRecorder.saveUserMessage(chat.getId(), resolvedChatId, historyType, msg);

        // 先决策再组装 Advisor，避免 SKIP 场景误把知识库上下文塞进 prompt
        RetrievalDecisionResult decision = retrievalDecisionService.decide(resolvedOptions);
        log.info("RAG 检索决策：chatId = {}，decision = {}，reason = {}，query = {}",
                resolvedChatId, decision.decision(), decision.reason(), resolvedOptions.getQuery());
        ChatClient.ChatClientRequestSpec requestSpec = ragChatClient.prompt();
        if (decision.decision() == RetrievalDecision.SKIP) {
            // SKIP 只启用会话文件上下文和 ChatMemory，不返回知识库 references
            requestSpec.advisors(spec -> contextFileChatSupport.contextFileParams(spec,
                    resolvedOptions.getTenantId(), resolvedOptions.getUserId(), resolvedChatId, msg, uploaded));
        } else {
            // RETRIEVE 同时启用文件上下文和知识库检索，done 事件需要从 ChatClientResponse 组装 references
            requestSpec.advisors(spec -> {
                contextFileChatSupport.contextFileParams(spec, resolvedOptions.getTenantId(),
                        resolvedOptions.getUserId(), resolvedChatId, msg, uploaded);
                spec.advisors(retrievalAdvisorFactory.create(vectorStore, chatModel, resolvedOptions,
                        retrievalFilterExpressionFactory.create(resolvedOptions)));
            });
        }
        requestSpec.user(msg);

        // includeReferences 控制 done 事件是否读取检索 metadata 并归档 references
        return chatStreamEventAssembler.chatClientResponseStream(requestSpec, chat.getId(), resolvedChatId,
                historyType, decision.decision() == RetrievalDecision.RETRIEVE);
    }

    private String messageOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
