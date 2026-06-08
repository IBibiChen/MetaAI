package com.metax.controller;

import com.metax.chat.MetaChatDO;
import com.metax.chat.MetaChatService;
import com.metax.chat.MetaChatUpsertRequest;
import com.metax.chat.file.MetaChatFileResponse;
import com.metax.chat.file.MetaChatFileService;
import com.metax.chat.history.MetaChatHistoryRole;
import com.metax.chat.history.MetaChatHistoryService;
import com.metax.chat.history.MetaChatHistoryType;
import com.metax.controller.request.*;
import com.metax.rag.etl.model.DocumentSourceType;
import com.metax.rag.indexing.DocumentIndexingRequest;
import com.metax.rag.indexing.DocumentIndexingRun;
import com.metax.rag.indexing.DocumentIndexingService;
import com.metax.rag.retrieval.advisor.MetaContextFile;
import com.metax.rag.retrieval.advisor.MetaContextFileAdvisor;
import com.metax.rag.retrieval.advisor.MetaContextFileKeys;
import com.metax.rag.retrieval.advisor.RetrievalAdvisorFactory;
import com.metax.rag.retrieval.assembly.RetrievalResponseAssembler;
import com.metax.rag.retrieval.decision.RetrievalDecision;
import com.metax.rag.retrieval.decision.RetrievalDecisionResult;
import com.metax.rag.retrieval.decision.RetrievalDecisionService;
import com.metax.rag.retrieval.filter.RetrievalFilterExpressionFactory;
import com.metax.rag.retrieval.model.*;
import com.metax.rag.retrieval.search.RetrievalSearchService;
import com.metax.rag.retrieval.stream.ChatStreamDelta;
import com.metax.rag.retrieval.stream.ChatStreamDone;
import com.metax.rag.retrieval.stream.ChatStreamError;
import com.metax.rag.retrieval.stream.ChatStreamMeta;
import com.metax.rag.retrieval.trace.RetrievalTrace;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ChatController .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/28
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "智能问答", description = "模型直连、记忆对话、知识库问答和文档索引调试接口")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private static final String DEFAULT_CHAT_ID = "tenantId:userId:sessionId";

    private static final String DEFAULT_CHAT_MESSAGE = "你是谁";

    private static final String DEFAULT_FILE_CHAT_MESSAGE = "总结一下这个文件";

    private static final String DEFAULT_RAG_FILE_CHAT_MESSAGE = "对比知识库方案和上传文件";

    private static final String DEFAULT_VISIBILITY = "PUBLIC";

    private final ChatClient chatClient;

    private final ChatClient ragChatClient;

    private final ChatModel chatModel;

    private final VectorStore vectorStore;

    private final DocumentIndexingService documentIndexingService;

    private final RetrievalAdvisorFactory retrievalAdvisorFactory;

    private final RetrievalFilterExpressionFactory retrievalFilterExpressionFactory;

    private final RetrievalResponseAssembler retrievalResponseAssembler;

    private final RetrievalSearchService retrievalSearchService;

    private final RetrievalDecisionService retrievalDecisionService;

    private final MetaChatHistoryService metaChatHistoryService;

    private final MetaChatService metaChatService;

    private final MetaChatFileService metaChatFileService;

    private final MetaContextFileAdvisor metaContextFileAdvisor;

    /**
     * 默认记忆对话
     *
     * <p>
     * 模型 provider 由 spring.ai.model.chat 配置决定
     * 默认记忆后端固定使用 redisChatMemory
     *
     * @param request 记忆对话请求参数
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/chat")
    @Operation(summary = "默认记忆对话", description = "使用当前配置选中的 ChatModel 和 ChatMemory 进行多轮对话")
    public String chat(@Valid @ParameterObject ChatRequest request) {

        String msg = messageOrDefault(request.getMsg(), DEFAULT_CHAT_MESSAGE);
        return memoryChat(request.getChatId(), request.getTenantId(), request.getUserId(), msg,
                MetaChatHistoryType.CHAT);
    }

    /**
     * 默认记忆对话，支持聊天文件
     *
     * <p>
     * 本接口复用 /v1/chat 入口，multipart 只用于携带会话级文件
     * 文件只绑定当前 chatId，不进入知识库，也不会被 /v1/rag 检索
     *
     * @param request 记忆对话文件请求参数
     * @return 文件上下文对话响应
     */
    @PostMapping(value = "/v1/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "默认记忆对话，支持聊天文件", description = "在现有聊天入口中上传文件并基于文件内容总结或问答")
    public MetaChatFileResponse chatWithFiles(@Valid @ModelAttribute ChatFileRequest request) {

        String msg = messageOrDefault(request.getMsg(), DEFAULT_FILE_CHAT_MESSAGE);
        return fileChat(request.getChatId(), request.getTenantId(), request.getUserId(), msg, request.getFiles());
    }

    /**
     * 默认记忆对话流式返回
     *
     * <p>
     * 使用 SSE 返回 meta、delta、done 和 error 事件
     * 模型完整回答会在流结束后写入完整聊天历史
     *
     * @param request 记忆对话请求参数
     * @return SSE 流式事件
     */
    @GetMapping(value = "/v1/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "默认记忆对话流式返回", description = "使用当前配置选中的 ChatModel 和 ChatMemory 进行多轮流式对话")
    public Flux<ServerSentEvent<Object>> chatStream(@Valid @ParameterObject ChatRequest request) {

        String msg = messageOrDefault(request.getMsg(), DEFAULT_CHAT_MESSAGE);
        String resolvedChatId = resolveChatId(request.getChatId());
        MetaChatDO chat = getOrCreateChat(resolvedChatId, request.getTenantId(), request.getUserId(),
                MetaChatHistoryType.CHAT, msg, null);
        metaChatHistoryService.saveUserMessage(chat.getId(), resolvedChatId, MetaChatHistoryType.CHAT, msg);
        metaChatService.updateLastMessage(chat.getId(), MetaChatHistoryRole.USER, msg);

        ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt()
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, resolvedChatId))
                .user(msg);
        return contentStream(requestSpec, chat.getId(), resolvedChatId, MetaChatHistoryType.CHAT);
    }

    /**
     * 默认记忆对话流式返回，支持聊天文件
     *
     * <p>
     * multipart 入口用于上传会话级临时文件，文件上下文由 MetaContextFileAdvisor 注入
     *
     * @param request 记忆对话文件请求参数
     * @return SSE 流式事件
     */
    @PostMapping(value = "/v1/chat/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "默认记忆对话流式返回，支持聊天文件", description = "上传临时文件并基于文件内容进行流式总结或问答")
    public Flux<ServerSentEvent<Object>> chatStreamWithFiles(@Valid @ModelAttribute ChatFileRequest request) {

        String msg = messageOrDefault(request.getMsg(), DEFAULT_FILE_CHAT_MESSAGE);
        return fileStreamChat(request.getChatId(), request.getTenantId(), request.getUserId(), msg,
                request.getFiles());
    }

    /**
     * RAG 检索增强对话
     *
     * <p>
     * ChatModel、EmbeddingModel 和 VectorStore 都由配置文件决定
     * 默认 ChatClient 固定使用 redisChatMemory
     *
     * @param request 知识库问答请求参数
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/rag")
    @Operation(summary = "知识库问答", description = "使用当前配置选中的模型、记忆和知识库进行问答")
    public RetrievalChatResponse rag(@Valid @ParameterObject RetrievalChatRequest request) {

        String msg = messageOrDefault(request.getMsg(), DEFAULT_CHAT_MESSAGE);
        return ragChat(request.getChatId(), msg, retrievalOptions(request, msg), MetaChatHistoryType.RAG);
    }

    /**
     * RAG 检索增强对话，支持聊天文件
     *
     * <p>
     * 知识库上下文由 RetrievalAugmentationAdvisor 注入，临时文件上下文由 MetaContextFileAdvisor 注入
     *
     * @param request 知识库问答文件请求参数
     * @return 模型响应内容
     */
    @PostMapping(value = "/v1/rag", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "知识库问答，支持聊天文件", description = "同时参考知识库和本次会话上传文件进行总结、问答或对比")
    public RetrievalChatResponse ragWithFiles(@Valid @ModelAttribute RetrievalChatFileRequest request) {

        String msg = messageOrDefault(request.getMsg(), DEFAULT_RAG_FILE_CHAT_MESSAGE);
        return ragChat(request.getChatId(), msg, retrievalOptions(request, msg), MetaChatHistoryType.RAG,
                request.getFiles());
    }

    /**
     * RAG 检索增强对话流式返回
     *
     * <p>
     * 使用 SSE 返回 meta、delta、done 和 error 事件
     * done 事件中返回完整 answer、chatId 和轻量 references
     *
     * @param request 知识库问答请求参数
     * @return SSE 流式事件
     */
    @GetMapping(value = "/v1/rag/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "知识库问答流式返回", description = "使用当前配置选中的模型、记忆和知识库进行流式问答")
    public Flux<ServerSentEvent<Object>> ragStream(@Valid @ParameterObject RetrievalChatRequest request) {

        String msg = messageOrDefault(request.getMsg(), DEFAULT_CHAT_MESSAGE);
        return ragStreamChat(request.getChatId(), msg, retrievalOptions(request, msg), MetaChatHistoryType.RAG);
    }

    /**
     * RAG 检索增强对话流式返回，支持聊天文件
     *
     * @param request 知识库问答文件请求参数
     * @return SSE 流式事件
     */
    @PostMapping(value = "/v1/rag/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "知识库问答流式返回，支持聊天文件", description = "同时参考知识库和本次会话上传文件进行流式总结、问答或对比")
    public Flux<ServerSentEvent<Object>> ragStreamWithFiles(@Valid @ModelAttribute RetrievalChatFileRequest request) {

        String msg = messageOrDefault(request.getMsg(), DEFAULT_RAG_FILE_CHAT_MESSAGE);
        return ragStreamChat(request.getChatId(), msg, retrievalOptions(request, msg), MetaChatHistoryType.RAG,
                request.getFiles());
    }

    /**
     * RAG 检索增强详情对话
     *
     * <p>
     * 返回模型回答和本次检索命中的引用来源，便于排查 topK、metadata filter 和 chunk 命中质量
     *
     * @param request 知识库检索调试请求参数
     * @return 知识库问答调试详情
     */
    @PostMapping(value = "/v1/rag/details")
    @Operation(summary = "知识库问答调试详情", description = "返回 answer、references 和 trace，用于调试召回质量、过滤条件和后处理效果")
    public RetrievalChatDetailsResponse ragDetails(@Valid @ParameterObject RetrievalDetailsRequest request) {

        String resolvedChatId = resolveChatId(request.getChatId());
        RetrievalOptions options = retrievalOptions(request);
        Filter.Expression filter = retrievalFilterExpressionFactory.create(options);
        RetrievalTrace.Builder traceBuilder = RetrievalTrace.builder(request.getMsg())
                .filter(String.valueOf(filter))
                .topK(request.getTopK())
                .similarityThreshold(request.getThreshold());

        ChatClient.ChatClientRequestSpec requestSpec = ragChatClient.prompt()
                .advisors(spec -> {
                    spec.param(ChatMemory.CONVERSATION_ID, resolvedChatId);
                    spec.param(RetrievalTrace.CONTEXT_KEY, traceBuilder);
                    spec.advisors(retrievalAdvisorFactory.create(vectorStore, chatModel, options, filter));
                })
                .user(request.getMsg());

        MetaChatDO chat = getOrCreateChat(resolvedChatId, request.getTenantId(), request.getUserId(),
                MetaChatHistoryType.RAG_DETAILS, request.getMsg(), request.getKbId());
        metaChatHistoryService.saveUserMessage(chat.getId(), resolvedChatId, MetaChatHistoryType.RAG_DETAILS,
                request.getMsg());
        metaChatService.updateLastMessage(chat.getId(), MetaChatHistoryRole.USER, request.getMsg());
        RetrievalChatDetailsResponse response = retrievalResponseAssembler.details(requestSpec.call().chatClientResponse(),
                resolvedChatId);
        metaChatHistoryService.saveAssistantMessage(chat.getId(), resolvedChatId, MetaChatHistoryType.RAG_DETAILS,
                response.answer(), List.of());
        metaChatService.updateLastMessage(chat.getId(), MetaChatHistoryRole.ASSISTANT, response.answer());
        return response;
    }

    /**
     * RAG 直接检索调试
     *
     * <p>
     * 绕过 ChatClient 和 ChatModel，直接查看 VectorStore 在当前过滤条件下召回的 chunk
     *
     * @param request 知识库检索调试请求参数
     * @return 直接检索响应
     */
    @PostMapping(value = "/v1/rag/search")
    @Operation(summary = "知识库检索调试", description = "绕过 ChatClient 和 ChatModel，直接返回向量库召回的 chunk")
    public RetrievalSearchResponse ragSearch(@Valid @ParameterObject RetrievalDetailsRequest request) {
        return retrievalSearchService.search(vectorStore, retrievalOptions(request));
    }

    /**
     * 从对象存储既有对象创建 RAG 异步文档索引执行
     *
     * @param request 对象存储文档索引导入请求参数
     * @return 文档索引执行
     */
    @PostMapping(value = "/v1/rag/documents/import")
    @Operation(summary = "从对象存储创建知识库文档索引任务", description = "原始文件必须已经归档到对象存储，本接口只创建异步 ETL 索引任务")
    public DocumentIndexingRun importRagDocument(@Valid @ParameterObject DocumentImportRequest request) {
        return documentIndexingService.submit(DocumentIndexingRequest.builder()
                .tenantId(request.getTenantId())
                .kbId(request.getKbId())
                .documentId(request.getDocumentId())
                .visibility(valueOrDefault(request.getVisibility(), DEFAULT_VISIBILITY))
                .deptId(request.getDeptId())
                .userId(request.getUserId())
                .documentType(request.getDocumentType())
                .sourceType(DocumentSourceType.OBJECT_STORAGE)
                .source(request.getSource())
                .filename(filenameFromPath(request.getObjectKey()))
                .bucket(request.getBucket())
                .objectKey(request.getObjectKey())
                .build());
    }

    /**
     * 从受控本地目录创建 RAG 异步文档索引执行
     *
     * @param request 本地文档索引导入请求参数
     * @return 文档索引执行
     */
    @PostMapping(value = "/v1/rag/documents/import/local")
    @Operation(summary = "从受控本地目录创建知识库文档索引任务", description = "path 必须是 metax.ai.rag.storage.local-root 下的相对路径")
    public DocumentIndexingRun importLocalRagDocument(@Valid @ParameterObject LocalDocumentImportRequest request) {
        return documentIndexingService.importLocalFile(request.getTenantId(), request.getKbId(),
                request.getDocumentId(), valueOrDefault(request.getVisibility(), DEFAULT_VISIBILITY),
                request.getDeptId(), request.getUserId(), request.getDocumentType(), request.getPath(),
                request.getSource());
    }

    /**
     * 查询 RAG 异步文档索引执行
     *
     * @param runId 执行 ID
     * @return 文档索引执行
     */
    @GetMapping(value = "/v1/rag/documents/runs/{runId}")
    @Operation(summary = "查询知识库文档索引任务", description = "根据 runId 查询异步索引状态、写入 chunk 数和失败原因")
    public DocumentIndexingRun getDocumentIndexingRun(
            @Parameter(description = "文档索引执行 ID", example = "c2a6bb6d-b0e6-4c40-9f32-3b08b5b19d62",
                    required = true, in = ParameterIn.PATH)
            @PathVariable String runId) {
        return documentIndexingService.getRun(runId);
    }

    /**
     * 当前 ChatModel 直连
     *
     * @param request 模型直连请求参数
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/model")
    @Operation(summary = "当前 ChatModel 直连", description = "绕过 ChatClient 和 ChatMemory，直接调用当前配置选中的 ChatModel")
    public String model(@Valid @ParameterObject ModelChatRequest request) {
        return chatModel.call(messageOrDefault(request.getMsg(), DEFAULT_CHAT_MESSAGE));
    }

    /**
     * 使用预配置 ChatClient 对话
     *
     * @param chatId 会话 ID
     * @param msg            消息
     * @return 模型响应内容
     */
    private String memoryChat(String chatId,
                              String tenantId,
                              String userId,
                              String msg,
                              MetaChatHistoryType historyType) {
        String resolvedChatId = resolveChatId(chatId);
        MetaChatDO chat = getOrCreateChat(resolvedChatId, tenantId, userId, historyType, msg, null);

        metaChatHistoryService.saveUserMessage(chat.getId(), resolvedChatId, historyType, msg);
        metaChatService.updateLastMessage(chat.getId(), MetaChatHistoryRole.USER, msg);
        String answer = chatClient.prompt()
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, resolvedChatId))
                .user(msg)
                .call()
                .content();
        metaChatHistoryService.saveAssistantMessage(chat.getId(), resolvedChatId, historyType, answer);
        metaChatService.updateLastMessage(chat.getId(), MetaChatHistoryRole.ASSISTANT, answer);
        return answer;
    }

    /**
     * 执行会话文件普通对话
     *
     * <p>
     * 有上传文件或历史 READY 文件时走 FILE_CHAT 历史类型，并通过 MetaContextFileAdvisor 注入文件上下文
     * 没有任何可用文件时退回普通记忆对话，避免无文件请求被错误归档为文件对话
     *
     * @param chatId 会话 ID
     * @param tenantId       租户 ID
     * @param userId         用户 ID
     * @param msg            用户消息
     * @param files          本轮上传文件
     * @return 文件对话响应
     */
    private MetaChatFileResponse fileChat(String chatId,
                                      String tenantId,
                                      String userId,
                                      String msg,
                                      MultipartFile[] files) {
        String resolvedChatId = resolveChatId(chatId);
        ChatScope scope = resolveScope(resolvedChatId, tenantId, userId);
        if (scope.tenantId() == null || scope.tenantId().isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (scope.userId() == null || scope.userId().isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        List<MetaContextFile> uploaded = metaChatFileService.uploadAndIndex(scope.tenantId(), scope.userId(),
                resolvedChatId, files);
        List<MetaContextFile> contextFiles = uploaded.isEmpty()
                ? metaChatFileService.readyFiles(scope.tenantId(), scope.userId(), resolvedChatId)
                : uploaded;
        if (contextFiles.isEmpty()) {
            String answer = memoryChat(resolvedChatId, scope.tenantId(), scope.userId(), msg,
                    MetaChatHistoryType.CHAT);
            return new MetaChatFileResponse(answer, resolvedChatId, List.of());
        }

        MetaChatDO chat = getOrCreateChat(resolvedChatId, scope.tenantId(), scope.userId(),
                MetaChatHistoryType.FILE_CHAT, msg, null);
        metaChatHistoryService.saveUserMessage(chat.getId(), resolvedChatId, MetaChatHistoryType.FILE_CHAT, msg);
        metaChatService.updateLastMessage(chat.getId(), MetaChatHistoryRole.USER, msg);
        ChatClientResponse response = chatClient.prompt()
                .advisors(spec -> contextFileParams(spec, scope.tenantId(), scope.userId(), resolvedChatId,
                        msg, uploaded))
                .user(msg)
                .call()
                .chatClientResponse();
        String answer = content(response);
        metaChatHistoryService.saveAssistantMessage(chat.getId(), resolvedChatId, MetaChatHistoryType.FILE_CHAT,
                answer);
        metaChatService.updateLastMessage(chat.getId(), MetaChatHistoryRole.ASSISTANT, answer);
        return new MetaChatFileResponse(answer, resolvedChatId, files(response));
    }

    /**
     * 执行会话文件流式对话
     *
     * <p>
     * 流式文件对话需要读取 ChatClientResponse metadata 中的 CONTEXT_FILES
     * 因此这里复用 chatClientResponseStream，而不是只消费 stream().content()
     *
     * @param chatId 会话 ID
     * @param tenantId       租户 ID
     * @param userId         用户 ID
     * @param msg            用户消息
     * @param files          本轮上传文件
     * @return SSE 流式事件
     */
    private Flux<ServerSentEvent<Object>> fileStreamChat(String chatId,
                                                         String tenantId,
                                                         String userId,
                                                         String msg,
                                                         MultipartFile[] files) {
        String resolvedChatId = resolveChatId(chatId);
        ChatScope scope = resolveScope(resolvedChatId, tenantId, userId);
        if (scope.tenantId() == null || scope.tenantId().isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (scope.userId() == null || scope.userId().isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        List<MetaContextFile> uploaded = metaChatFileService.uploadAndIndex(scope.tenantId(), scope.userId(),
                resolvedChatId, files);
        MetaChatDO chat = getOrCreateChat(resolvedChatId, scope.tenantId(), scope.userId(),
                MetaChatHistoryType.FILE_CHAT, msg, null);
        metaChatHistoryService.saveUserMessage(chat.getId(), resolvedChatId, MetaChatHistoryType.FILE_CHAT, msg);
        metaChatService.updateLastMessage(chat.getId(), MetaChatHistoryRole.USER, msg);
        ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt()
                .advisors(spec -> contextFileParams(spec, scope.tenantId(), scope.userId(), resolvedChatId,
                        msg, uploaded))
                .user(msg);
        return chatClientResponseStream(requestSpec, chat.getId(), resolvedChatId, MetaChatHistoryType.FILE_CHAT,
                false);
    }

    private RetrievalChatResponse ragChat(String chatId,
                                          String msg,
                                          RetrievalOptions options,
                                          MetaChatHistoryType historyType) {
        return ragChat(chatId, msg, options, historyType, null);
    }

    /**
     * 执行 RAG 普通对话
     *
     * <p>
     * kbId 是接口、业务层和向量 metadata filter 统一使用的知识库边界字段
     * files 只进入 scope = session 的会话文件上下文，不会写入知识库 references
     *
     * @param chatId 会话 ID
     * @param msg            用户消息
     * @param options        RAG 检索参数
     * @param historyType    历史类型
     * @param files          本轮上传文件
     * @return RAG 对话响应
     */
    private RetrievalChatResponse ragChat(String chatId,
                                          String msg,
                                          RetrievalOptions options,
                                          MetaChatHistoryType historyType,
                                          MultipartFile[] files) {
        String resolvedChatId = resolveChatId(chatId);
        RetrievalOptions resolvedOptions = Objects.requireNonNull(options, "RetrievalOptions must not be null");
        List<MetaContextFile> uploaded = uploadFiles(resolvedOptions.getTenantId(), resolvedOptions.getUserId(),
                resolvedChatId, files);
        MetaChatDO chat = getOrCreateChat(resolvedChatId, resolvedOptions.getTenantId(),
                resolvedOptions.getUserId(), historyType, msg, resolvedOptions.getKbId());

        metaChatHistoryService.saveUserMessage(chat.getId(), resolvedChatId, historyType, msg);
        metaChatService.updateLastMessage(chat.getId(), MetaChatHistoryRole.USER, msg);
        RetrievalDecisionResult decision = retrievalDecisionService.decide(resolvedOptions);
        log.info("RAG 检索决策：chatId = {}，decision = {}，reason = {}，query = {}",
                resolvedChatId, decision.decision(), decision.reason(), resolvedOptions.getQuery());
        RetrievalChatResponse response;
        if (decision.decision() == RetrievalDecision.SKIP) {
            response = retrievalResponseAssembler.chatWithoutReferences(ragChatClient.prompt()
                    .advisors(spec -> contextFileParams(spec, resolvedOptions.getTenantId(),
                            resolvedOptions.getUserId(), resolvedChatId, msg, uploaded))
                    .user(msg)
                    .call()
                    .chatClientResponse(), resolvedChatId);
        } else {
            response = retrievalResponseAssembler.chat(ragChatClient.prompt()
                    .advisors(spec -> {
                        contextFileParams(spec, resolvedOptions.getTenantId(), resolvedOptions.getUserId(),
                                resolvedChatId, msg, uploaded);
                        spec.advisors(retrievalAdvisorFactory.create(vectorStore, chatModel, resolvedOptions,
                                retrievalFilterExpressionFactory.create(resolvedOptions)));
                    })
                    .user(msg)
                    .call()
                    .chatClientResponse(), resolvedChatId);
        }
        metaChatHistoryService.saveAssistantMessage(chat.getId(), resolvedChatId, historyType, response.answer(),
                response.references());
        metaChatService.updateLastMessage(chat.getId(), MetaChatHistoryRole.ASSISTANT, response.answer());
        return response;
    }

    private Flux<ServerSentEvent<Object>> ragStreamChat(String chatId,
                                                        String msg,
                                                        RetrievalOptions options,
                                                        MetaChatHistoryType historyType) {
        return ragStreamChat(chatId, msg, options, historyType, null);
    }

    /**
     * 执行 RAG 流式对话
     *
     * <p>
     * 检索决策为 SKIP 时仍然保留会话文件上下文 Advisor，支持“只基于上传文件回答”的场景
     * 检索决策为 RETRIEVE 时同时注入会话文件上下文和知识库 RAG Advisor
     *
     * @param chatId 会话 ID
     * @param msg            用户消息
     * @param options        RAG 检索参数
     * @param historyType    历史类型
     * @param files          本轮上传文件
     * @return SSE 流式事件
     */
    private Flux<ServerSentEvent<Object>> ragStreamChat(String chatId,
                                                        String msg,
                                                        RetrievalOptions options,
                                                        MetaChatHistoryType historyType,
                                                        MultipartFile[] files) {
        String resolvedChatId = resolveChatId(chatId);
        RetrievalOptions resolvedOptions = Objects.requireNonNull(options, "RetrievalOptions must not be null");
        List<MetaContextFile> uploaded = uploadFiles(resolvedOptions.getTenantId(), resolvedOptions.getUserId(),
                resolvedChatId, files);
        MetaChatDO chat = getOrCreateChat(resolvedChatId, resolvedOptions.getTenantId(),
                resolvedOptions.getUserId(), historyType, msg, resolvedOptions.getKbId());

        metaChatHistoryService.saveUserMessage(chat.getId(), resolvedChatId, historyType, msg);
        metaChatService.updateLastMessage(chat.getId(), MetaChatHistoryRole.USER, msg);
        RetrievalDecisionResult decision = retrievalDecisionService.decide(resolvedOptions);
        log.info("RAG 检索决策：chatId = {}，decision = {}，reason = {}，query = {}",
                resolvedChatId, decision.decision(), decision.reason(), resolvedOptions.getQuery());
        ChatClient.ChatClientRequestSpec requestSpec = ragChatClient.prompt();
        if (decision.decision() == RetrievalDecision.SKIP) {
            requestSpec.advisors(spec -> contextFileParams(spec, resolvedOptions.getTenantId(),
                    resolvedOptions.getUserId(), resolvedChatId, msg, uploaded));
        } else {
            requestSpec.advisors(spec -> {
                contextFileParams(spec, resolvedOptions.getTenantId(), resolvedOptions.getUserId(),
                        resolvedChatId, msg, uploaded);
                spec.advisors(retrievalAdvisorFactory.create(vectorStore, chatModel, resolvedOptions,
                        retrievalFilterExpressionFactory.create(resolvedOptions)));
            });
        }
        requestSpec.user(msg);
        return chatClientResponseStream(requestSpec, chat.getId(), resolvedChatId, historyType,
                decision.decision() == RetrievalDecision.RETRIEVE);
    }

    /**
     * 普通聊天文本流
     *
     * <p>
     * 普通聊天只需要模型增量文本，不需要读取 ChatClientResponse context 或 metadata
     * 这里使用 stream().content() 展示最轻量的 Spring AI 流式用法
     *
     * @param requestSpec    ChatClient 请求
     * @param fkId       会话主表 ID
     * @param chatId     会话 ID
     * @param historyType    历史类型
     * @return SSE 流式事件
     */
    private Flux<ServerSentEvent<Object>> contentStream(ChatClient.ChatClientRequestSpec requestSpec,
                                                        Long fkId,
                                                        String chatId,
                                                        MetaChatHistoryType historyType) {
        StringBuilder answer = new StringBuilder();
        Flux<ServerSentEvent<Object>> meta = Flux.just(event("meta", new ChatStreamMeta(chatId)));
        Flux<ServerSentEvent<Object>> body = requestSpec.stream()
                .content()
                .filter(content -> content != null && !content.isEmpty())
                .doOnNext(answer::append)
                .map(content -> event("delta", new ChatStreamDelta(content)));
        Mono<ServerSentEvent<Object>> done = Mono.fromSupplier(() -> {
            String fullAnswer = answer.toString();
            metaChatHistoryService.saveAssistantMessage(fkId, chatId, historyType, fullAnswer);
            metaChatService.updateLastMessage(fkId, MetaChatHistoryRole.ASSISTANT, fullAnswer);
            return event("done", new ChatStreamDone(fullAnswer, chatId, List.of()));
        });
        return meta.concatWith(body).concatWith(done)
                .onErrorResume(ex -> {
                    log.error("流式对话发生异常：chatId = {}", chatId, ex);
                    return Flux.just(event("error", new ChatStreamError("系统异常")));
                });
    }

    /**
     * RAG 深层响应流
     *
     * <p>
     * RAG 流式完成事件需要读取 RetrievalAugmentationAdvisor 写入的 context / metadata
     * 这里必须使用 stream().chatClientResponse()，不能退化成只返回文本的 content()
     *
     * @param requestSpec       ChatClient 请求
     * @param fkId             会话主表 ID
     * @param chatId           会话 ID
     * @param historyType       历史类型
     * @param includeReferences 是否组装 RAG 引用
     * @return SSE 流式事件
     */
    private Flux<ServerSentEvent<Object>> chatClientResponseStream(ChatClient.ChatClientRequestSpec requestSpec,
                                                                   Long fkId,
                                                                   String chatId,
                                                                   MetaChatHistoryType historyType,
                                                                   boolean includeReferences) {
        StringBuilder answer = new StringBuilder();
        AtomicReference<ChatClientResponse> lastResponse = new AtomicReference<>(ChatClientResponse.builder().build());
        Flux<ServerSentEvent<Object>> meta = Flux.just(event("meta", new ChatStreamMeta(chatId)));
        Flux<ServerSentEvent<Object>> body = requestSpec.stream()
                .chatClientResponse()
                .doOnNext(lastResponse::set)
                .map(this::content)
                .filter(content -> content != null && !content.isEmpty())
                .doOnNext(answer::append)
                .map(content -> event("delta", new ChatStreamDelta(content)));
        Mono<ServerSentEvent<Object>> done = Mono.fromSupplier(() -> {
            String fullAnswer = answer.toString();
            ChatStreamDone data;
            List<RetrievalDocumentReference> references = List.of();
            List<MetaContextFile> files = files(lastResponse.get());
            if (includeReferences) {
                RetrievalChatResponse response = retrievalResponseAssembler.streamChat(fullAnswer, lastResponse.get(),
                        chatId);
                references = response.references();
                files = response.files();
                data = new ChatStreamDone(response.answer(), response.chatId(), response.references(),
                        response.files());
            } else {
                data = new ChatStreamDone(fullAnswer, chatId, List.of(), files);
            }
            metaChatHistoryService.saveAssistantMessage(fkId, chatId, historyType, fullAnswer, references);
            metaChatService.updateLastMessage(fkId, MetaChatHistoryRole.ASSISTANT, fullAnswer);
            return event("done", data);
        });
        return meta.concatWith(body).concatWith(done)
                .onErrorResume(ex -> {
                    log.error("流式对话发生异常：chatId = {}", chatId, ex);
                    return Flux.just(event("error", new ChatStreamError("系统异常")));
                });
    }

    private String content(ChatClientResponse response) {
        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse == null || chatResponse.getResult() == null) {
            return null;
        }
        AssistantMessage output = chatResponse.getResult().getOutput();
        return output == null ? null : output.getText();
    }

    /**
     * 注入会话文件 Advisor 参数
     *
     * <p>
     * ORIGINAL_USER_QUERY 保留用户原始问题，避免文件检索使用已被其他 Advisor 增强后的 prompt
     * INCOMING_FILES 只表示本轮新上传文件，Advisor 会在为空时自行回退到当前会话 READY 文件
     *
     * @param spec           AdvisorSpec
     * @param tenantId       租户 ID
     * @param userId         用户 ID
     * @param chatId 会话 ID
     * @param msg            原始用户消息
     * @param uploaded       本轮新上传文件
     */
    private void contextFileParams(ChatClient.AdvisorSpec spec,
                                   String tenantId,
                                   String userId,
                                   String chatId,
                                   String msg,
                                   List<MetaContextFile> uploaded) {
        ChatScope scope = resolveScope(chatId, tenantId, userId);
        spec.param(ChatMemory.CONVERSATION_ID, chatId);
        spec.param(MetaContextFileKeys.TENANT_ID, scope.tenantId());
        spec.param(MetaContextFileKeys.USER_ID, scope.userId());
        spec.param(MetaContextFileKeys.CHAT_ID, chatId);
        spec.param(MetaContextFileKeys.ORIGINAL_USER_QUERY, msg);
        spec.param(MetaContextFileKeys.INCOMING_FILES, uploaded == null ? List.of() : uploaded);
        spec.advisors(metaContextFileAdvisor);
    }

    /**
     * 上传并索引本轮会话文件
     *
     * <p>
     * 无文件时直接返回空列表，不触发 readyFiles 查询
     * 这样可以让 Advisor 统一决定是否使用历史 READY 文件
     *
     * @param tenantId       租户 ID
     * @param userId         用户 ID
     * @param chatId 会话 ID
     * @param files          本轮上传文件
     * @return 本轮新上传文件
     */
    private List<MetaContextFile> uploadFiles(String tenantId,
                                              String userId,
                                              String chatId,
                                              MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return List.of();
        }
        ChatScope scope = resolveScope(chatId, tenantId, userId);
        if (scope.tenantId() == null || scope.tenantId().isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (scope.userId() == null || scope.userId().isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        return metaChatFileService.uploadAndIndex(scope.tenantId(), scope.userId(), chatId, files);
    }

    /**
     * 从 ChatClientResponse 中读取本次参与上下文增强的会话文件
     *
     * <p>
     * 普通响应优先读 ChatResponse metadata，流式兜底读 ChatClientResponse context
     *
     * @param response ChatClientResponse
     * @return 会话文件上下文
     */
    @SuppressWarnings("unchecked")
    private List<MetaContextFile> files(ChatClientResponse response) {
        Object value = null;
        if (response.chatResponse() != null) {
            value = response.chatResponse().getMetadata().get(MetaContextFileKeys.CONTEXT_FILES);
        }
        if (value == null) {
            value = response.context().get(MetaContextFileKeys.CONTEXT_FILES);
        }
        if (!(value instanceof List<?> list) || !list.stream().allMatch(MetaContextFile.class::isInstance)) {
            return List.of();
        }
        return (List<MetaContextFile>) list;
    }

    private ServerSentEvent<Object> event(String event, Object data) {
        return ServerSentEvent.builder()
                .event(event)
                .data(data)
                .build();
    }

    private RetrievalOptions retrievalOptions(RetrievalChatRequest request, String msg) {
        validateRetrievalScope(request.getTenantId(), request.getKbId());
        return RetrievalOptions.builder()
                .tenantId(request.getTenantId())
                .kbId(request.getKbId())
                .documentId(request.getDocumentId())
                .documentType(request.getDocumentType())
                .userId(request.getUserId())
                .deptIds(parseCsv(request.getDeptIds()))
                .query(msg)
                .build();
    }

    private RetrievalOptions retrievalOptions(RetrievalChatFileRequest request, String msg) {
        validateRetrievalScope(request.getTenantId(), request.getKbId());
        return RetrievalOptions.builder()
                .tenantId(request.getTenantId())
                .kbId(request.getKbId())
                .documentId(request.getDocumentId())
                .documentType(request.getDocumentType())
                .userId(request.getUserId())
                .deptIds(parseCsv(request.getDeptIds()))
                .query(msg)
                .build();
    }

    private RetrievalOptions retrievalOptions(RetrievalDetailsRequest request) {
        validateRetrievalScope(request.getTenantId(), request.getKbId());
        return RetrievalOptions.builder()
                .tenantId(request.getTenantId())
                .kbId(request.getKbId())
                .documentId(request.getDocumentId())
                .documentType(request.getDocumentType())
                .userId(request.getUserId())
                .deptIds(parseCsv(request.getDeptIds()))
                .topK(request.getTopK())
                .similarityThreshold(request.getThreshold())
                .filterExpression(request.getFilterExpression())
                .query(request.getMsg())
                .build();
    }

    private void validateRetrievalScope(String tenantId, String kbId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (kbId == null || kbId.isBlank()) {
            throw new IllegalArgumentException("kbId must not be blank");
        }
    }

    private String messageOrDefault(String value, String defaultValue) {
        return valueOrDefault(value, defaultValue);
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String resolveChatId(String chatId) {
        return chatId == null || chatId.isBlank() ? DEFAULT_CHAT_ID : chatId;
    }

    private MetaChatDO getOrCreateChat(String chatId,
                                       String tenantId,
                                       String userId,
                                       MetaChatHistoryType chatMode,
                                       String firstMessage,
                                       String kbId) {
        ChatScope scope = resolveScope(chatId, tenantId, userId);
        return metaChatService.getOrCreate(new MetaChatUpsertRequest(scope.tenantId(), scope.userId(), chatId,
                chatMode, firstMessage, kbId, null, null, "console"));
    }

    private ChatScope resolveScope(String chatId, String tenantId, String userId) {
        String resolvedTenantId = tenantId;
        String resolvedUserId = userId;
        if ((resolvedTenantId == null || resolvedTenantId.isBlank())
                || (resolvedUserId == null || resolvedUserId.isBlank())) {
            String[] parts = chatId == null ? new String[0] : chatId.split(":");
            if ((resolvedTenantId == null || resolvedTenantId.isBlank()) && parts.length > 0) {
                resolvedTenantId = parts[0];
            }
            if ((resolvedUserId == null || resolvedUserId.isBlank()) && parts.length > 1) {
                resolvedUserId = parts[1];
            }
        }
        if (resolvedTenantId == null || resolvedTenantId.isBlank()) {
            resolvedTenantId = "tenantId";
        }
        if (resolvedUserId == null || resolvedUserId.isBlank()) {
            resolvedUserId = "userId";
        }
        return new ChatScope(resolvedTenantId, resolvedUserId);
    }

    private String filenameFromPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalized = path.replace("\\", "/");
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private List<String> parseCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(candidate -> !candidate.isBlank())
                .toList();
    }

    private record ChatScope(
            String tenantId,
            String userId
    ) {
    }
}
