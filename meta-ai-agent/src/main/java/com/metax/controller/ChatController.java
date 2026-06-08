package com.metax.controller;

import com.metax.chat.file.ChatFileResponse;
import com.metax.chat.file.ChatFileService;
import com.metax.history.ChatHistoryService;
import com.metax.history.ChatHistoryRole;
import com.metax.history.ChatHistoryType;
import com.metax.history.MetaChatDO;
import com.metax.history.MetaChatService;
import com.metax.history.MetaChatUpsertRequest;
import com.metax.rag.retrieval.ChatStreamDelta;
import com.metax.rag.retrieval.ChatStreamDone;
import com.metax.rag.retrieval.ChatStreamError;
import com.metax.rag.retrieval.ChatStreamMeta;
import com.metax.rag.etl.model.DocumentSourceType;
import com.metax.rag.indexing.DocumentIndexingRequest;
import com.metax.rag.indexing.DocumentIndexingRun;
import com.metax.rag.indexing.DocumentIndexingService;
import com.metax.rag.retrieval.RetrievalAdvisorFactory;
import com.metax.rag.retrieval.RetrievalChatDetailsResponse;
import com.metax.rag.retrieval.RetrievalChatResponse;
import com.metax.rag.retrieval.RetrievalDecision;
import com.metax.rag.retrieval.RetrievalDecisionResult;
import com.metax.rag.retrieval.RetrievalDecisionService;
import com.metax.rag.retrieval.RetrievalFilterExpressionFactory;
import com.metax.rag.retrieval.RetrievalOptions;
import com.metax.rag.retrieval.RetrievalResponseAssembler;
import com.metax.rag.retrieval.RetrievalSearchResponse;
import com.metax.rag.retrieval.RetrievalSearchService;
import com.metax.rag.retrieval.RetrievalTrace;
import com.metax.rag.retrieval.RetrievalCitation;
import com.metax.rag.retrieval.MetaContextFile;
import com.metax.rag.retrieval.MetaContextFileAdvisor;
import com.metax.rag.retrieval.MetaContextFileKeys;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
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
@Tag(name = "智能问答与 RAG", description = "模型直连、记忆对话、RAG 检索增强和文档索引调试接口")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private static final String DEFAULT_CONVERSATION_ID = "tenantId:userId:sessionId";

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

    private final ChatHistoryService chatHistoryService;

    private final MetaChatService metaChatService;

    private final ChatFileService chatFileService;

    private final MetaContextFileAdvisor metaContextFileAdvisor;

    public ChatController(@Qualifier("chatClient") ChatClient chatClient,
                          @Qualifier("ragChatClient") ChatClient ragChatClient,
                          ChatModel chatModel,
                          VectorStore vectorStore,
                          DocumentIndexingService documentIndexingService,
                          RetrievalAdvisorFactory retrievalAdvisorFactory,
                          RetrievalFilterExpressionFactory retrievalFilterExpressionFactory,
                          RetrievalResponseAssembler retrievalResponseAssembler,
                          RetrievalSearchService retrievalSearchService,
                          RetrievalDecisionService retrievalDecisionService,
                          ChatHistoryService chatHistoryService,
                          MetaChatService metaChatService,
                          ChatFileService chatFileService,
                          MetaContextFileAdvisor metaContextFileAdvisor) {
        this.chatClient = chatClient;
        this.ragChatClient = ragChatClient;
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
        this.documentIndexingService = documentIndexingService;
        this.retrievalAdvisorFactory = retrievalAdvisorFactory;
        this.retrievalFilterExpressionFactory = retrievalFilterExpressionFactory;
        this.retrievalResponseAssembler = retrievalResponseAssembler;
        this.retrievalSearchService = retrievalSearchService;
        this.retrievalDecisionService = retrievalDecisionService;
        this.chatHistoryService = chatHistoryService;
        this.metaChatService = metaChatService;
        this.chatFileService = chatFileService;
        this.metaContextFileAdvisor = metaContextFileAdvisor;
    }

    /**
     * 默认记忆对话
     *
     * <p>
     * 模型 provider 由 spring.ai.model.chat 配置决定
     * 默认记忆后端固定使用 redisChatMemory
     *
     * @param conversationId 会话 ID
     * @param msg            消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/chat")
    @Operation(summary = "默认记忆对话", description = "使用当前配置选中的 ChatModel 和 ChatMemory 进行多轮对话")
    public String chat(
            @Parameter(description = "会话 ID，建议格式：tenantId:userId:sessionId", example = "t1:u1:s1")
            @RequestParam(name = "conversationId", required = false) String conversationId,
            @Parameter(description = "租户 ID", example = "t1")
            @RequestParam(name = "tenantId", required = false) String tenantId,
            @Parameter(description = "用户 ID", example = "u1")
            @RequestParam(name = "userId", required = false) String userId,
            @Parameter(description = "用户消息", example = "你是谁")
            @RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return memoryChat(conversationId, tenantId, userId, msg, ChatHistoryType.CHAT);
    }

    /**
     * 默认记忆对话，支持聊天文件
     *
     * <p>
     * 本接口复用 /v1/chat 入口，multipart 只用于携带会话级文件
     * 文件只绑定当前 conversation，不进入知识库，也不会被 /v1/rag 检索
     *
     * @param conversationId 会话 ID
     * @param tenantId       租户 ID
     * @param userId         用户 ID
     * @param msg            消息
     * @param files          聊天文件
     * @return 文件上下文对话响应
     */
    @PostMapping(value = "/v1/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "默认记忆对话，支持聊天文件", description = "在现有聊天入口中上传文件并基于文件内容总结或问答")
    public ChatFileResponse chatWithFiles(
            @Parameter(description = "会话 ID，建议格式：tenantId:userId:sessionId", example = "t1:u1:s1")
            @RequestParam(name = "conversationId", required = false) String conversationId,
            @Parameter(description = "租户 ID", example = "t1")
            @RequestParam(name = "tenantId", required = false) String tenantId,
            @Parameter(description = "用户 ID", example = "u1")
            @RequestParam(name = "userId", required = false) String userId,
            @Parameter(description = "用户消息", example = "总结一下这个文件")
            @RequestParam(name = "msg", defaultValue = "总结一下这个文件") String msg,
            @Parameter(description = "聊天文件")
            @RequestPart(name = "files", required = false) MultipartFile[] files) {
        return fileChat(conversationId, tenantId, userId, msg, files);
    }

    /**
     * 默认记忆对话流式返回
     *
     * <p>
     * 使用 SSE 返回 meta、delta、done 和 error 事件
     * 模型完整回答会在流结束后写入完整聊天历史
     *
     * @param conversationId 会话 ID
     * @param msg            消息
     * @return SSE 流式事件
     */
    @GetMapping(value = "/v1/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "默认记忆对话流式返回", description = "使用当前配置选中的 ChatModel 和 ChatMemory 进行多轮流式对话")
    public Flux<ServerSentEvent<Object>> chatStream(
            @Parameter(description = "会话 ID，建议格式：tenantId:userId:sessionId", example = "t1:u1:s1")
            @RequestParam(name = "conversationId", required = false) String conversationId,
            @Parameter(description = "租户 ID", example = "t1")
            @RequestParam(name = "tenantId", required = false) String tenantId,
            @Parameter(description = "用户 ID", example = "u1")
            @RequestParam(name = "userId", required = false) String userId,
            @Parameter(description = "用户消息", example = "你是谁")
            @RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        String resolvedConversationId = resolveConversationId(conversationId);
        MetaChatDO chat = getOrCreateChat(resolvedConversationId, tenantId, userId, ChatHistoryType.CHAT, msg, null);
        chatHistoryService.saveUserMessage(chat.getId(), resolvedConversationId, ChatHistoryType.CHAT, msg);
        metaChatService.updateLastMessage(chat.getId(), ChatHistoryRole.USER, msg);
        ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt()
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, resolvedConversationId))
                .user(msg);
        return contentStream(requestSpec, chat.getId(), resolvedConversationId, ChatHistoryType.CHAT);
    }

    /**
     * 默认记忆对话流式返回，支持聊天文件
     *
     * <p>
     * multipart 入口用于上传会话级临时文件，文件上下文由 MetaContextFileAdvisor 注入
     *
     * @param conversationId 会话 ID
     * @param tenantId       租户 ID
     * @param userId         用户 ID
     * @param msg            消息
     * @param files          聊天文件
     * @return SSE 流式事件
     */
    @PostMapping(value = "/v1/chat/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "默认记忆对话流式返回，支持聊天文件", description = "上传临时文件并基于文件内容进行流式总结或问答")
    public Flux<ServerSentEvent<Object>> chatStreamWithFiles(
            @Parameter(description = "会话 ID，建议格式：tenantId:userId:sessionId", example = "t1:u1:s1")
            @RequestParam(name = "conversationId", required = false) String conversationId,
            @Parameter(description = "租户 ID", example = "t1")
            @RequestParam(name = "tenantId", required = false) String tenantId,
            @Parameter(description = "用户 ID", example = "u1")
            @RequestParam(name = "userId", required = false) String userId,
            @Parameter(description = "用户消息", example = "总结一下这个文件")
            @RequestParam(name = "msg", defaultValue = "总结一下这个文件") String msg,
            @Parameter(description = "聊天文件")
            @RequestPart(name = "files", required = false) MultipartFile[] files) {
        return fileStreamChat(conversationId, tenantId, userId, msg, files);
    }

    /**
     * RAG 检索增强对话
     *
     * <p>
     * ChatModel、EmbeddingModel 和 VectorStore 都由配置文件决定
     * 默认 ChatClient 固定使用 redisChatMemory
     *
     * @param conversationId  会话 ID
     * @param msg             消息
     * @param tenantId        租户 ID
     * @param knowledgeBaseId 知识库 ID
     * @param documentId      文档 ID
     * @param documentType    文档类型
     * @param userId          用户 ID
     * @param deptIds         可访问部门 ID 列表
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/rag")
    @Operation(summary = "RAG 检索增强对话", description = "使用当前配置选中的模型、记忆和向量库进行检索增强对话")
    public RetrievalChatResponse rag(
            @Parameter(description = "会话 ID，建议格式：tenantId:userId:sessionId", example = "t1:u1:s1")
            @RequestParam(name = "conversationId", required = false) String conversationId,
            @Parameter(description = "用户消息", example = "Spring AI 的 RAG 是什么")
            @RequestParam(name = "msg", defaultValue = "你是谁") String msg,
            @Parameter(description = "租户 ID，RAG 查询强制过滤字段", example = "t1")
            @RequestParam(name = "tenantId", required = false) String tenantId,
            @Parameter(description = "知识库 ID，RAG 查询强制过滤字段", example = "kb1")
            @RequestParam(name = "knowledgeBaseId", required = false) String knowledgeBaseId,
            @Parameter(description = "文档 ID，可选收窄条件", example = "doc-001")
            @RequestParam(name = "documentId", required = false) String documentId,
            @Parameter(description = "文档类型，可选收窄条件，例如 txt、md、json、tika", example = "md")
            @RequestParam(name = "documentType", required = false) String documentType,
            @Parameter(description = "当前用户 ID，用于用户私有文档过滤", example = "u1")
            @RequestParam(name = "userId", required = false) String userId,
            @Parameter(description = "当前用户可访问部门 ID 列表，多个用英文逗号分隔", example = "d1,d2")
            @RequestParam(name = "deptIds", required = false) String deptIds) {
        validateRetrievalScope(tenantId, knowledgeBaseId);
        RetrievalOptions options = RetrievalOptions.builder()
                .tenantId(tenantId)
                .knowledgeBaseId(knowledgeBaseId)
                .documentId(documentId)
                .documentType(documentType)
                .userId(userId)
                .deptIds(parseCsv(deptIds))
                .query(msg)
                .build();
        return ragChat(conversationId, msg, options, ChatHistoryType.RAG);
    }

    /**
     * RAG 检索增强对话，支持聊天文件
     *
     * <p>
     * 知识库上下文由 RetrievalAugmentationAdvisor 注入，临时文件上下文由 MetaContextFileAdvisor 注入
     *
     * @param conversationId  会话 ID
     * @param msg             消息
     * @param tenantId        租户 ID
     * @param knowledgeBaseId 知识库 ID
     * @param documentId      文档 ID
     * @param documentType    文档类型
     * @param userId          用户 ID
     * @param deptIds         可访问部门 ID 列表
     * @param files           聊天文件
     * @return 模型响应内容
     */
    @PostMapping(value = "/v1/rag", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "RAG 检索增强对话，支持聊天文件", description = "同时参考知识库和本次会话上传文件进行总结、问答或对比")
    public RetrievalChatResponse ragWithFiles(
            @Parameter(description = "会话 ID，建议格式：tenantId:userId:sessionId", example = "t1:u1:s1")
            @RequestParam(name = "conversationId", required = false) String conversationId,
            @Parameter(description = "用户消息", example = "对比知识库方案和上传文件")
            @RequestParam(name = "msg", defaultValue = "对比知识库方案和上传文件") String msg,
            @Parameter(description = "租户 ID，RAG 查询强制过滤字段", example = "t1")
            @RequestParam(name = "tenantId", required = false) String tenantId,
            @Parameter(description = "知识库 ID，RAG 查询强制过滤字段", example = "kb1")
            @RequestParam(name = "knowledgeBaseId", required = false) String knowledgeBaseId,
            @Parameter(description = "文档 ID，可选收窄条件", example = "doc-001")
            @RequestParam(name = "documentId", required = false) String documentId,
            @Parameter(description = "文档类型，可选收窄条件，例如 txt、md、json、tika", example = "md")
            @RequestParam(name = "documentType", required = false) String documentType,
            @Parameter(description = "当前用户 ID，用于用户私有文档过滤", example = "u1")
            @RequestParam(name = "userId", required = false) String userId,
            @Parameter(description = "当前用户可访问部门 ID 列表，多个用英文逗号分隔", example = "d1,d2")
            @RequestParam(name = "deptIds", required = false) String deptIds,
            @Parameter(description = "聊天文件")
            @RequestPart(name = "files", required = false) MultipartFile[] files) {
        validateRetrievalScope(tenantId, knowledgeBaseId);
        RetrievalOptions options = RetrievalOptions.builder()
                .tenantId(tenantId)
                .knowledgeBaseId(knowledgeBaseId)
                .documentId(documentId)
                .documentType(documentType)
                .userId(userId)
                .deptIds(parseCsv(deptIds))
                .query(msg)
                .build();
        return ragChat(conversationId, msg, options, ChatHistoryType.RAG, files);
    }

    /**
     * RAG 检索增强对话流式返回
     *
     * <p>
     * 使用 SSE 返回 meta、delta、done 和 error 事件
     * done 事件中返回完整 answer、conversationId 和轻量 references
     *
     * @param conversationId  会话 ID
     * @param msg             消息
     * @param tenantId        租户 ID
     * @param knowledgeBaseId 知识库 ID
     * @param documentId      文档 ID
     * @param documentType    文档类型
     * @param userId          用户 ID
     * @param deptIds         可访问部门 ID 列表
     * @return SSE 流式事件
     */
    @GetMapping(value = "/v1/rag/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "RAG 检索增强对话流式返回", description = "使用当前配置选中的模型、记忆和向量库进行检索增强流式对话")
    public Flux<ServerSentEvent<Object>> ragStream(
            @Parameter(description = "会话 ID，建议格式：tenantId:userId:sessionId", example = "t1:u1:s1")
            @RequestParam(name = "conversationId", required = false) String conversationId,
            @Parameter(description = "用户消息", example = "Spring AI 的 RAG 是什么")
            @RequestParam(name = "msg", defaultValue = "你是谁") String msg,
            @Parameter(description = "租户 ID，RAG 查询强制过滤字段", example = "t1")
            @RequestParam(name = "tenantId", required = false) String tenantId,
            @Parameter(description = "知识库 ID，RAG 查询强制过滤字段", example = "kb1")
            @RequestParam(name = "knowledgeBaseId", required = false) String knowledgeBaseId,
            @Parameter(description = "文档 ID，可选收窄条件", example = "doc-001")
            @RequestParam(name = "documentId", required = false) String documentId,
            @Parameter(description = "文档类型，可选收窄条件，例如 txt、md、json、tika", example = "md")
            @RequestParam(name = "documentType", required = false) String documentType,
            @Parameter(description = "当前用户 ID，用于用户私有文档过滤", example = "u1")
            @RequestParam(name = "userId", required = false) String userId,
            @Parameter(description = "当前用户可访问部门 ID 列表，多个用英文逗号分隔", example = "d1,d2")
            @RequestParam(name = "deptIds", required = false) String deptIds) {
        validateRetrievalScope(tenantId, knowledgeBaseId);
        RetrievalOptions options = RetrievalOptions.builder()
                .tenantId(tenantId)
                .knowledgeBaseId(knowledgeBaseId)
                .documentId(documentId)
                .documentType(documentType)
                .userId(userId)
                .deptIds(parseCsv(deptIds))
                .query(msg)
                .build();
        return ragStreamChat(conversationId, msg, options, ChatHistoryType.RAG);
    }

    /**
     * RAG 检索增强对话流式返回，支持聊天文件
     *
     * @param conversationId  会话 ID
     * @param msg             消息
     * @param tenantId        租户 ID
     * @param knowledgeBaseId 知识库 ID
     * @param documentId      文档 ID
     * @param documentType    文档类型
     * @param userId          用户 ID
     * @param deptIds         可访问部门 ID 列表
     * @param files           聊天文件
     * @return SSE 流式事件
     */
    @PostMapping(value = "/v1/rag/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "RAG 检索增强对话流式返回，支持聊天文件", description = "同时参考知识库和本次会话上传文件进行流式总结、问答或对比")
    public Flux<ServerSentEvent<Object>> ragStreamWithFiles(
            @Parameter(description = "会话 ID，建议格式：tenantId:userId:sessionId", example = "t1:u1:s1")
            @RequestParam(name = "conversationId", required = false) String conversationId,
            @Parameter(description = "用户消息", example = "对比知识库方案和上传文件")
            @RequestParam(name = "msg", defaultValue = "对比知识库方案和上传文件") String msg,
            @Parameter(description = "租户 ID，RAG 查询强制过滤字段", example = "t1")
            @RequestParam(name = "tenantId", required = false) String tenantId,
            @Parameter(description = "知识库 ID，RAG 查询强制过滤字段", example = "kb1")
            @RequestParam(name = "knowledgeBaseId", required = false) String knowledgeBaseId,
            @Parameter(description = "文档 ID，可选收窄条件", example = "doc-001")
            @RequestParam(name = "documentId", required = false) String documentId,
            @Parameter(description = "文档类型，可选收窄条件，例如 txt、md、json、tika", example = "md")
            @RequestParam(name = "documentType", required = false) String documentType,
            @Parameter(description = "当前用户 ID，用于用户私有文档过滤", example = "u1")
            @RequestParam(name = "userId", required = false) String userId,
            @Parameter(description = "当前用户可访问部门 ID 列表，多个用英文逗号分隔", example = "d1,d2")
            @RequestParam(name = "deptIds", required = false) String deptIds,
            @Parameter(description = "聊天文件")
            @RequestPart(name = "files", required = false) MultipartFile[] files) {
        validateRetrievalScope(tenantId, knowledgeBaseId);
        RetrievalOptions options = RetrievalOptions.builder()
                .tenantId(tenantId)
                .knowledgeBaseId(knowledgeBaseId)
                .documentId(documentId)
                .documentType(documentType)
                .userId(userId)
                .deptIds(parseCsv(deptIds))
                .query(msg)
                .build();
        return ragStreamChat(conversationId, msg, options, ChatHistoryType.RAG, files);
    }

    /**
     * RAG 检索增强详情对话
     *
     * <p>
     * 返回模型回答和本次检索命中的引用来源，便于排查 topK、metadata filter 和 chunk 命中质量
     *
     * @param conversationId   会话 ID
     * @param msg              消息
     * @param tenantId         租户 ID
     * @param knowledgeBaseId  知识库 ID
     * @param documentId       文档 ID
     * @param documentType     文档类型
     * @param userId           用户 ID
     * @param deptIds          可访问部门 ID 列表
     * @param topK             检索数量
     * @param threshold        相似度阈值
     * @param filterExpression 原始过滤表达式，仅用于 trace 调试展示
     * @return RAG 详情响应
     */
    @PostMapping(value = "/v1/rag/details")
    @Operation(summary = "RAG 检索增强详情对话", description = "返回 answer、references 和 trace，用于调试召回质量、过滤条件和后处理效果")
    public RetrievalChatDetailsResponse ragDetails(
            @Parameter(description = "会话 ID，建议格式：tenantId:userId:sessionId", example = "t1:u1:s1")
            @RequestParam(name = "conversationId", required = false) String conversationId,
            @Parameter(description = "用户消息", example = "Spring AI 的 ETL 是什么", required = true)
            @RequestParam(name = "msg") String msg,
            @Parameter(description = "租户 ID，RAG 查询强制过滤字段", example = "t1", required = true)
            @RequestParam(name = "tenantId") String tenantId,
            @Parameter(description = "知识库 ID，RAG 查询强制过滤字段", example = "kb1", required = true)
            @RequestParam(name = "knowledgeBaseId") String knowledgeBaseId,
            @Parameter(description = "文档 ID，可选收窄条件", example = "doc-001")
            @RequestParam(name = "documentId", required = false) String documentId,
            @Parameter(description = "文档类型，可选收窄条件，例如 txt、md、json、tika", example = "md")
            @RequestParam(name = "documentType", required = false) String documentType,
            @Parameter(description = "当前用户 ID，用于用户私有文档过滤", example = "u1")
            @RequestParam(name = "userId", required = false) String userId,
            @Parameter(description = "当前用户可访问部门 ID 列表，多个用英文逗号分隔", example = "d1,d2")
            @RequestParam(name = "deptIds", required = false) String deptIds,
            @Parameter(description = "本次检索 topK，不传时使用全局配置", example = "5")
            @RequestParam(name = "topK", required = false) Integer topK,
            @Parameter(description = "本次检索相似度阈值，不传时使用全局配置", example = "0.5")
            @RequestParam(name = "threshold", required = false) Double threshold,
            @Parameter(description = "原始过滤表达式，仅用于 trace 调试展示，实际检索使用结构化权限过滤",
                    example = "tenantId == 't1' && kbId == 'kb1'")
            @RequestParam(name = "filterExpression", required = false) String filterExpression) {
        String resolvedConversationId = resolveConversationId(conversationId);
        validateRetrievalScope(tenantId, knowledgeBaseId);
        RetrievalOptions options = RetrievalOptions.builder()
                .tenantId(tenantId)
                .knowledgeBaseId(knowledgeBaseId)
                .documentId(documentId)
                .documentType(documentType)
                .userId(userId)
                .deptIds(parseCsv(deptIds))
                .topK(topK)
                .similarityThreshold(threshold)
                .filterExpression(filterExpression)
                .query(msg)
                .build();
        Filter.Expression filter = retrievalFilterExpressionFactory.create(options);
        RetrievalTrace.Builder traceBuilder = RetrievalTrace.builder(msg)
                .filter(String.valueOf(filter))
                .topK(topK)
                .similarityThreshold(threshold);

        ChatClient.ChatClientRequestSpec requestSpec = ragChatClient.prompt()
                .advisors(spec -> {
                    spec.param(ChatMemory.CONVERSATION_ID, resolvedConversationId);
                    spec.param(RetrievalTrace.CONTEXT_KEY, traceBuilder);
                    spec.advisors(retrievalAdvisorFactory.create(vectorStore, chatModel, options, filter));
                })
                .user(msg);

        MetaChatDO chat = getOrCreateChat(resolvedConversationId, tenantId, userId, ChatHistoryType.RAG_DETAILS, msg,
                knowledgeBaseId);
        chatHistoryService.saveUserMessage(chat.getId(), resolvedConversationId, ChatHistoryType.RAG_DETAILS, msg);
        metaChatService.updateLastMessage(chat.getId(), ChatHistoryRole.USER, msg);
        RetrievalChatDetailsResponse response = retrievalResponseAssembler.details(requestSpec.call().chatClientResponse(),
                resolvedConversationId);
        chatHistoryService.saveAssistantMessage(chat.getId(), resolvedConversationId, ChatHistoryType.RAG_DETAILS,
                response.answer(), List.of());
        metaChatService.updateLastMessage(chat.getId(), ChatHistoryRole.ASSISTANT, response.answer());
        return response;
    }

    /**
     * RAG 直接检索调试
     *
     * <p>
     * 绕过 ChatClient 和 ChatModel，直接查看 VectorStore 在当前过滤条件下召回的 chunk
     *
     * @param msg              检索 query
     * @param tenantId         租户 ID
     * @param knowledgeBaseId  知识库 ID
     * @param documentId       文档 ID
     * @param documentType     文档类型
     * @param userId           用户 ID
     * @param deptIds          可访问部门 ID 列表
     * @param topK             检索数量
     * @param threshold        相似度阈值
     * @param filterExpression 原始过滤表达式，仅用于 trace 调试展示
     * @return 直接检索响应
     */
    @PostMapping(value = "/v1/rag/search")
    @Operation(summary = "RAG 直接检索调试", description = "绕过 ChatClient 和 ChatModel，直接返回 VectorStore 召回的 chunk")
    public RetrievalSearchResponse ragSearch(
            @Parameter(description = "检索 query", example = "Spring AI 的 ETL 是什么", required = true)
            @RequestParam(name = "msg") String msg,
            @Parameter(description = "租户 ID，RAG 查询强制过滤字段", example = "t1", required = true)
            @RequestParam(name = "tenantId") String tenantId,
            @Parameter(description = "知识库 ID，RAG 查询强制过滤字段", example = "kb1", required = true)
            @RequestParam(name = "knowledgeBaseId") String knowledgeBaseId,
            @Parameter(description = "文档 ID，可选收窄条件", example = "doc-001")
            @RequestParam(name = "documentId", required = false) String documentId,
            @Parameter(description = "文档类型，可选收窄条件，例如 txt、md、json、tika", example = "md")
            @RequestParam(name = "documentType", required = false) String documentType,
            @Parameter(description = "当前用户 ID，用于用户私有文档过滤", example = "u1")
            @RequestParam(name = "userId", required = false) String userId,
            @Parameter(description = "当前用户可访问部门 ID 列表，多个用英文逗号分隔", example = "d1,d2")
            @RequestParam(name = "deptIds", required = false) String deptIds,
            @Parameter(description = "本次检索 topK，不传时使用全局配置", example = "5")
            @RequestParam(name = "topK", required = false) Integer topK,
            @Parameter(description = "本次检索相似度阈值，不传时使用全局配置", example = "0.5")
            @RequestParam(name = "threshold", required = false) Double threshold,
            @Parameter(description = "原始过滤表达式，仅用于 trace 调试展示，实际检索使用结构化权限过滤",
                    example = "tenantId == 't1' && kbId == 'kb1'")
            @RequestParam(name = "filterExpression", required = false) String filterExpression) {
        validateRetrievalScope(tenantId, knowledgeBaseId);
        RetrievalOptions options = RetrievalOptions.builder()
                .tenantId(tenantId)
                .knowledgeBaseId(knowledgeBaseId)
                .documentId(documentId)
                .documentType(documentType)
                .userId(userId)
                .deptIds(parseCsv(deptIds))
                .topK(topK)
                .similarityThreshold(threshold)
                .filterExpression(filterExpression)
                .query(msg)
                .build();
        return retrievalSearchService.search(vectorStore, options);
    }

    /**
     * 从对象存储既有对象创建 RAG 异步文档索引执行
     *
     * @param tenantId        租户 ID
     * @param knowledgeBaseId 知识库 ID
     * @param documentId      文档 ID
     * @param visibility      文档可见性
     * @param deptId          部门 ID
     * @param userId          用户 ID
     * @param documentType    文档类型
     * @param source          来源标识
     * @param bucket          对象存储 bucket
     * @param objectKey       对象存储 object key
     * @return 文档索引执行
     */
    @PostMapping(value = "/v1/rag/documents/import")
    @Operation(summary = "从对象存储创建 RAG 文档索引执行", description = "原始文件必须已经归档到对象存储，本接口只创建异步 ETL 索引执行")
    public DocumentIndexingRun importRagDocument(
            @Parameter(description = "租户 ID", example = "t1", required = true)
            @RequestParam(name = "tenantId") String tenantId,
            @Parameter(description = "知识库 ID", example = "kb1", required = true)
            @RequestParam(name = "knowledgeBaseId") String knowledgeBaseId,
            @Parameter(description = "文档 ID，同一 documentId 重复导入会覆盖旧 chunk", example = "doc-001",
                    required = true)
            @RequestParam(name = "documentId") String documentId,
            @Parameter(description = "文档可见性，可选值：PUBLIC、DEPT、USER", example = "PUBLIC")
            @RequestParam(name = "visibility", defaultValue = "PUBLIC") String visibility,
            @Parameter(description = "部门 ID，visibility=DEPT 时必填", example = "d1")
            @RequestParam(name = "deptId", required = false) String deptId,
            @Parameter(description = "用户 ID，visibility=USER 时必填", example = "u1")
            @RequestParam(name = "userId", required = false) String userId,
            @Parameter(description = "文档类型，可为空，为空时根据 objectKey 后缀识别", example = "md")
            @RequestParam(name = "documentType", required = false) String documentType,
            @Parameter(description = "来源标识，可用于前端展示引用来源", example = "knowledge/t1/kb1/demo.md")
            @RequestParam(name = "source", required = false) String source,
            @Parameter(description = "对象存储 bucket", example = "meta-ai-knowledge", required = true)
            @RequestParam(name = "bucket") String bucket,
            @Parameter(description = "对象存储 object key", example = "knowledge/t1/kb1/demo.md", required = true)
            @RequestParam(name = "objectKey") String objectKey) {
        return documentIndexingService.submit(DocumentIndexingRequest.builder()
                .tenantId(tenantId)
                .knowledgeBaseId(knowledgeBaseId)
                .documentId(documentId)
                .visibility(visibility)
                .deptId(deptId)
                .userId(userId)
                .documentType(documentType)
                .sourceType(DocumentSourceType.OBJECT_STORAGE)
                .source(source)
                .filename(filenameFromPath(objectKey))
                .bucket(bucket)
                .objectKey(objectKey)
                .build());
    }

    /**
     * 从受控本地目录创建 RAG 异步文档索引执行
     *
     * @param tenantId        租户 ID
     * @param knowledgeBaseId 知识库 ID
     * @param documentId      文档 ID
     * @param visibility      文档可见性
     * @param deptId          部门 ID
     * @param userId          用户 ID
     * @param documentType    文档类型
     * @param source          来源标识
     * @param path            本地文件相对路径
     * @return 文档索引执行
     */
    @PostMapping(value = "/v1/rag/documents/import/local")
    @Operation(summary = "从受控本地目录创建 RAG 文档索引执行", description = "path 必须是 metax.ai.rag.storage.local-root 下的相对路径")
    public DocumentIndexingRun importLocalRagDocument(
            @Parameter(description = "租户 ID", example = "t1", required = true)
            @RequestParam(name = "tenantId") String tenantId,
            @Parameter(description = "知识库 ID", example = "kb1", required = true)
            @RequestParam(name = "knowledgeBaseId") String knowledgeBaseId,
            @Parameter(description = "文档 ID，同一 documentId 重复导入会覆盖旧 chunk", example = "doc-001",
                    required = true)
            @RequestParam(name = "documentId") String documentId,
            @Parameter(description = "文档可见性，可选值：PUBLIC、DEPT、USER", example = "PUBLIC")
            @RequestParam(name = "visibility", defaultValue = "PUBLIC") String visibility,
            @Parameter(description = "部门 ID，visibility=DEPT 时必填", example = "d1")
            @RequestParam(name = "deptId", required = false) String deptId,
            @Parameter(description = "用户 ID，visibility=USER 时必填", example = "u1")
            @RequestParam(name = "userId", required = false) String userId,
            @Parameter(description = "文档类型，可为空，为空时根据 path 后缀识别", example = "md")
            @RequestParam(name = "documentType", required = false) String documentType,
            @Parameter(description = "来源标识，可用于前端展示引用来源", example = "local/demo.md")
            @RequestParam(name = "source", required = false) String source,
            @Parameter(description = "本地知识库文件相对路径", example = "demo.md", required = true)
            @RequestParam(name = "path") String path) {
        return documentIndexingService.importLocalFile(tenantId, knowledgeBaseId, documentId, visibility, deptId,
                userId, documentType, path, source);
    }

    /**
     * 查询 RAG 异步文档索引执行
     *
     * @param runId 执行 ID
     * @return 文档索引执行
     */
    @GetMapping(value = "/v1/rag/documents/runs/{runId}")
    @Operation(summary = "查询 RAG 文档索引执行", description = "根据 runId 查询异步索引执行状态、写入 chunk 数和失败原因")
    public DocumentIndexingRun getDocumentIndexingRun(
            @Parameter(description = "文档索引执行 ID", example = "c2a6bb6d-b0e6-4c40-9f32-3b08b5b19d62",
                    required = true, in = ParameterIn.PATH)
            @PathVariable String runId) {
        return documentIndexingService.getRun(runId);
    }

    /**
     * 当前 ChatModel 直连
     *
     * @param msg 消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/model")
    @Operation(summary = "当前 ChatModel 直连", description = "绕过 ChatClient 和 ChatMemory，直接调用当前配置选中的 ChatModel")
    public String model(
            @Parameter(description = "用户消息", example = "你是谁")
            @RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return chatModel.call(msg);
    }

    /**
     * 使用预配置 ChatClient 对话
     *
     * @param conversationId 会话 ID
     * @param msg            消息
     * @return 模型响应内容
     */
    private String memoryChat(String conversationId,
                              String tenantId,
                              String userId,
                              String msg,
                              ChatHistoryType historyType) {
        String resolvedConversationId = resolveConversationId(conversationId);
        MetaChatDO chat = getOrCreateChat(resolvedConversationId, tenantId, userId, historyType, msg, null);

        chatHistoryService.saveUserMessage(chat.getId(), resolvedConversationId, historyType, msg);
        metaChatService.updateLastMessage(chat.getId(), ChatHistoryRole.USER, msg);
        String answer = chatClient.prompt()
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, resolvedConversationId))
                .user(msg)
                .call()
                .content();
        chatHistoryService.saveAssistantMessage(chat.getId(), resolvedConversationId, historyType, answer);
        metaChatService.updateLastMessage(chat.getId(), ChatHistoryRole.ASSISTANT, answer);
        return answer;
    }

    /**
     * 执行会话文件普通对话
     *
     * <p>
     * 有上传文件或历史 READY 文件时走 FILE_CHAT 历史类型，并通过 MetaContextFileAdvisor 注入文件上下文
     * 没有任何可用文件时退回普通记忆对话，避免无文件请求被错误归档为文件对话
     *
     * @param conversationId 会话 ID
     * @param tenantId       租户 ID
     * @param userId         用户 ID
     * @param msg            用户消息
     * @param files          本轮上传文件
     * @return 文件对话响应
     */
    private ChatFileResponse fileChat(String conversationId,
                                      String tenantId,
                                      String userId,
                                      String msg,
                                      MultipartFile[] files) {
        String resolvedConversationId = resolveConversationId(conversationId);
        ConversationScope scope = resolveScope(resolvedConversationId, tenantId, userId);
        if (scope.tenantId() == null || scope.tenantId().isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (scope.userId() == null || scope.userId().isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        List<MetaContextFile> uploaded = chatFileService.uploadAndIndex(scope.tenantId(), scope.userId(),
                resolvedConversationId, files);
        List<MetaContextFile> contextFiles = uploaded.isEmpty()
                ? chatFileService.readyFiles(scope.tenantId(), scope.userId(), resolvedConversationId)
                : uploaded;
        if (contextFiles.isEmpty()) {
            String answer = memoryChat(resolvedConversationId, scope.tenantId(), scope.userId(), msg,
                    ChatHistoryType.CHAT);
            return new ChatFileResponse(answer, resolvedConversationId, List.of());
        }

        MetaChatDO chat = getOrCreateChat(resolvedConversationId, scope.tenantId(), scope.userId(),
                ChatHistoryType.FILE_CHAT, msg, null);
        chatHistoryService.saveUserMessage(chat.getId(), resolvedConversationId, ChatHistoryType.FILE_CHAT, msg);
        metaChatService.updateLastMessage(chat.getId(), ChatHistoryRole.USER, msg);
        ChatClientResponse response = chatClient.prompt()
                .advisors(spec -> contextFileParams(spec, scope.tenantId(), scope.userId(), resolvedConversationId,
                        msg, uploaded))
                .user(msg)
                .call()
                .chatClientResponse();
        String answer = content(response);
        chatHistoryService.saveAssistantMessage(chat.getId(), resolvedConversationId, ChatHistoryType.FILE_CHAT,
                answer);
        metaChatService.updateLastMessage(chat.getId(), ChatHistoryRole.ASSISTANT, answer);
        return new ChatFileResponse(answer, resolvedConversationId, files(response));
    }

    /**
     * 执行会话文件流式对话
     *
     * <p>
     * 流式文件对话需要读取 ChatClientResponse metadata 中的 CONTEXT_FILES
     * 因此这里复用 chatClientResponseStream，而不是只消费 stream().content()
     *
     * @param conversationId 会话 ID
     * @param tenantId       租户 ID
     * @param userId         用户 ID
     * @param msg            用户消息
     * @param files          本轮上传文件
     * @return SSE 流式事件
     */
    private Flux<ServerSentEvent<Object>> fileStreamChat(String conversationId,
                                                         String tenantId,
                                                         String userId,
                                                         String msg,
                                                         MultipartFile[] files) {
        String resolvedConversationId = resolveConversationId(conversationId);
        ConversationScope scope = resolveScope(resolvedConversationId, tenantId, userId);
        if (scope.tenantId() == null || scope.tenantId().isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (scope.userId() == null || scope.userId().isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        List<MetaContextFile> uploaded = chatFileService.uploadAndIndex(scope.tenantId(), scope.userId(),
                resolvedConversationId, files);
        MetaChatDO chat = getOrCreateChat(resolvedConversationId, scope.tenantId(), scope.userId(),
                ChatHistoryType.FILE_CHAT, msg, null);
        chatHistoryService.saveUserMessage(chat.getId(), resolvedConversationId, ChatHistoryType.FILE_CHAT, msg);
        metaChatService.updateLastMessage(chat.getId(), ChatHistoryRole.USER, msg);
        ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt()
                .advisors(spec -> contextFileParams(spec, scope.tenantId(), scope.userId(), resolvedConversationId,
                        msg, uploaded))
                .user(msg);
        return chatClientResponseStream(requestSpec, chat.getId(), resolvedConversationId, ChatHistoryType.FILE_CHAT,
                false);
    }

    private RetrievalChatResponse ragChat(String conversationId,
                                          String msg,
                                          RetrievalOptions options,
                                          ChatHistoryType historyType) {
        return ragChat(conversationId, msg, options, historyType, null);
    }

    /**
     * 执行 RAG 普通对话
     *
     * <p>
     * knowledgeBaseId 是接口和业务层字段，进入向量 metadata filter 时会映射为 kbId
     * files 只进入 scope = session 的会话文件上下文，不会写入知识库 references
     *
     * @param conversationId 会话 ID
     * @param msg            用户消息
     * @param options        RAG 检索参数
     * @param historyType    历史类型
     * @param files          本轮上传文件
     * @return RAG 对话响应
     */
    private RetrievalChatResponse ragChat(String conversationId,
                                          String msg,
                                          RetrievalOptions options,
                                          ChatHistoryType historyType,
                                          MultipartFile[] files) {
        String resolvedConversationId = resolveConversationId(conversationId);
        RetrievalOptions resolvedOptions = Objects.requireNonNull(options, "RetrievalOptions must not be null");
        List<MetaContextFile> uploaded = uploadFiles(resolvedOptions.getTenantId(), resolvedOptions.getUserId(),
                resolvedConversationId, files);
        MetaChatDO chat = getOrCreateChat(resolvedConversationId, resolvedOptions.getTenantId(),
                resolvedOptions.getUserId(), historyType, msg, resolvedOptions.getKnowledgeBaseId());

        chatHistoryService.saveUserMessage(chat.getId(), resolvedConversationId, historyType, msg);
        metaChatService.updateLastMessage(chat.getId(), ChatHistoryRole.USER, msg);
        RetrievalDecisionResult decision = retrievalDecisionService.decide(resolvedOptions);
        log.info("RAG 检索决策：conversationId = {}，decision = {}，reason = {}，query = {}",
                resolvedConversationId, decision.decision(), decision.reason(), resolvedOptions.getQuery());
        RetrievalChatResponse response;
        if (decision.decision() == RetrievalDecision.SKIP) {
            response = retrievalResponseAssembler.chatWithoutReferences(ragChatClient.prompt()
                    .advisors(spec -> contextFileParams(spec, resolvedOptions.getTenantId(),
                            resolvedOptions.getUserId(), resolvedConversationId, msg, uploaded))
                    .user(msg)
                    .call()
                    .chatClientResponse(), resolvedConversationId);
        } else {
            response = retrievalResponseAssembler.chat(ragChatClient.prompt()
                    .advisors(spec -> {
                        contextFileParams(spec, resolvedOptions.getTenantId(), resolvedOptions.getUserId(),
                                resolvedConversationId, msg, uploaded);
                        spec.advisors(retrievalAdvisorFactory.create(vectorStore, chatModel, resolvedOptions,
                                retrievalFilterExpressionFactory.create(resolvedOptions)));
                    })
                    .user(msg)
                    .call()
                    .chatClientResponse(), resolvedConversationId);
        }
        chatHistoryService.saveAssistantMessage(chat.getId(), resolvedConversationId, historyType, response.answer(),
                response.references());
        metaChatService.updateLastMessage(chat.getId(), ChatHistoryRole.ASSISTANT, response.answer());
        return response;
    }

    private Flux<ServerSentEvent<Object>> ragStreamChat(String conversationId,
                                                        String msg,
                                                        RetrievalOptions options,
                                                        ChatHistoryType historyType) {
        return ragStreamChat(conversationId, msg, options, historyType, null);
    }

    /**
     * 执行 RAG 流式对话
     *
     * <p>
     * 检索决策为 SKIP 时仍然保留会话文件上下文 Advisor，支持“只基于上传文件回答”的场景
     * 检索决策为 RETRIEVE 时同时注入会话文件上下文和知识库 RAG Advisor
     *
     * @param conversationId 会话 ID
     * @param msg            用户消息
     * @param options        RAG 检索参数
     * @param historyType    历史类型
     * @param files          本轮上传文件
     * @return SSE 流式事件
     */
    private Flux<ServerSentEvent<Object>> ragStreamChat(String conversationId,
                                                        String msg,
                                                        RetrievalOptions options,
                                                        ChatHistoryType historyType,
                                                        MultipartFile[] files) {
        String resolvedConversationId = resolveConversationId(conversationId);
        RetrievalOptions resolvedOptions = Objects.requireNonNull(options, "RetrievalOptions must not be null");
        List<MetaContextFile> uploaded = uploadFiles(resolvedOptions.getTenantId(), resolvedOptions.getUserId(),
                resolvedConversationId, files);
        MetaChatDO chat = getOrCreateChat(resolvedConversationId, resolvedOptions.getTenantId(),
                resolvedOptions.getUserId(), historyType, msg, resolvedOptions.getKnowledgeBaseId());

        chatHistoryService.saveUserMessage(chat.getId(), resolvedConversationId, historyType, msg);
        metaChatService.updateLastMessage(chat.getId(), ChatHistoryRole.USER, msg);
        RetrievalDecisionResult decision = retrievalDecisionService.decide(resolvedOptions);
        log.info("RAG 检索决策：conversationId = {}，decision = {}，reason = {}，query = {}",
                resolvedConversationId, decision.decision(), decision.reason(), resolvedOptions.getQuery());
        ChatClient.ChatClientRequestSpec requestSpec = ragChatClient.prompt();
        if (decision.decision() == RetrievalDecision.SKIP) {
            requestSpec.advisors(spec -> contextFileParams(spec, resolvedOptions.getTenantId(),
                    resolvedOptions.getUserId(), resolvedConversationId, msg, uploaded));
        } else {
            requestSpec.advisors(spec -> {
                contextFileParams(spec, resolvedOptions.getTenantId(), resolvedOptions.getUserId(),
                        resolvedConversationId, msg, uploaded);
                spec.advisors(retrievalAdvisorFactory.create(vectorStore, chatModel, resolvedOptions,
                        retrievalFilterExpressionFactory.create(resolvedOptions)));
            });
        }
        requestSpec.user(msg);
        return chatClientResponseStream(requestSpec, chat.getId(), resolvedConversationId, historyType,
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
     * @param conversationId 会话 ID
     * @param historyType    历史类型
     * @return SSE 流式事件
     */
    private Flux<ServerSentEvent<Object>> contentStream(ChatClient.ChatClientRequestSpec requestSpec,
                                                        Long chatId,
                                                        String conversationId,
                                                        ChatHistoryType historyType) {
        StringBuilder answer = new StringBuilder();
        Flux<ServerSentEvent<Object>> meta = Flux.just(event("meta", new ChatStreamMeta(conversationId)));
        Flux<ServerSentEvent<Object>> body = requestSpec.stream()
                .content()
                .filter(content -> content != null && !content.isEmpty())
                .doOnNext(answer::append)
                .map(content -> event("delta", new ChatStreamDelta(content)));
        Mono<ServerSentEvent<Object>> done = Mono.fromSupplier(() -> {
            String fullAnswer = answer.toString();
            chatHistoryService.saveAssistantMessage(chatId, conversationId, historyType, fullAnswer);
            metaChatService.updateLastMessage(chatId, ChatHistoryRole.ASSISTANT, fullAnswer);
            return event("done", new ChatStreamDone(fullAnswer, conversationId, List.of()));
        });
        return meta.concatWith(body).concatWith(done)
                .onErrorResume(ex -> {
                    log.error("流式对话发生异常：conversationId = {}", conversationId, ex);
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
     * @param conversationId    会话 ID
     * @param historyType       历史类型
     * @param includeReferences 是否组装 RAG 引用
     * @return SSE 流式事件
     */
    private Flux<ServerSentEvent<Object>> chatClientResponseStream(ChatClient.ChatClientRequestSpec requestSpec,
                                                                   Long chatId,
                                                                   String conversationId,
                                                                   ChatHistoryType historyType,
                                                                   boolean includeReferences) {
        StringBuilder answer = new StringBuilder();
        AtomicReference<ChatClientResponse> lastResponse = new AtomicReference<>(ChatClientResponse.builder().build());
        Flux<ServerSentEvent<Object>> meta = Flux.just(event("meta", new ChatStreamMeta(conversationId)));
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
            List<RetrievalCitation> references = List.of();
            List<MetaContextFile> files = files(lastResponse.get());
            if (includeReferences) {
                RetrievalChatResponse response = retrievalResponseAssembler.streamChat(fullAnswer, lastResponse.get(),
                        conversationId);
                references = response.references();
                files = response.files();
                data = new ChatStreamDone(response.answer(), response.conversationId(), response.references(),
                        response.files());
            } else {
                data = new ChatStreamDone(fullAnswer, conversationId, List.of(), files);
            }
            chatHistoryService.saveAssistantMessage(chatId, conversationId, historyType, fullAnswer, references);
            metaChatService.updateLastMessage(chatId, ChatHistoryRole.ASSISTANT, fullAnswer);
            return event("done", data);
        });
        return meta.concatWith(body).concatWith(done)
                .onErrorResume(ex -> {
                    log.error("流式对话发生异常：conversationId = {}", conversationId, ex);
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
     * @param conversationId 会话 ID
     * @param msg            原始用户消息
     * @param uploaded       本轮新上传文件
     */
    private void contextFileParams(ChatClient.AdvisorSpec spec,
                                   String tenantId,
                                   String userId,
                                   String conversationId,
                                   String msg,
                                   List<MetaContextFile> uploaded) {
        ConversationScope scope = resolveScope(conversationId, tenantId, userId);
        spec.param(ChatMemory.CONVERSATION_ID, conversationId);
        spec.param(MetaContextFileKeys.TENANT_ID, scope.tenantId());
        spec.param(MetaContextFileKeys.USER_ID, scope.userId());
        spec.param(MetaContextFileKeys.CONVERSATION_ID, conversationId);
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
     * @param conversationId 会话 ID
     * @param files          本轮上传文件
     * @return 本轮新上传文件
     */
    private List<MetaContextFile> uploadFiles(String tenantId,
                                              String userId,
                                              String conversationId,
                                              MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return List.of();
        }
        ConversationScope scope = resolveScope(conversationId, tenantId, userId);
        if (scope.tenantId() == null || scope.tenantId().isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (scope.userId() == null || scope.userId().isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        return chatFileService.uploadAndIndex(scope.tenantId(), scope.userId(), conversationId, files);
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

    private void validateRetrievalScope(String tenantId, String knowledgeBaseId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (knowledgeBaseId == null || knowledgeBaseId.isBlank()) {
            throw new IllegalArgumentException("knowledgeBaseId must not be blank");
        }
    }

    private String resolveConversationId(String conversationId) {
        return conversationId == null || conversationId.isBlank() ? DEFAULT_CONVERSATION_ID : conversationId;
    }

    private MetaChatDO getOrCreateChat(String conversationId,
                                       String tenantId,
                                       String userId,
                                       ChatHistoryType chatMode,
                                       String firstMessage,
                                       String knowledgeBaseId) {
        ConversationScope scope = resolveScope(conversationId, tenantId, userId);
        return metaChatService.getOrCreate(new MetaChatUpsertRequest(scope.tenantId(), scope.userId(), conversationId,
                chatMode, firstMessage, knowledgeBaseId, null, null, "console"));
    }

    private ConversationScope resolveScope(String conversationId, String tenantId, String userId) {
        String resolvedTenantId = tenantId;
        String resolvedUserId = userId;
        if ((resolvedTenantId == null || resolvedTenantId.isBlank())
                || (resolvedUserId == null || resolvedUserId.isBlank())) {
            String[] parts = conversationId == null ? new String[0] : conversationId.split(":");
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
        return new ConversationScope(resolvedTenantId, resolvedUserId);
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

    private record ConversationScope(
            String tenantId,
            String userId
    ) {
    }
}
