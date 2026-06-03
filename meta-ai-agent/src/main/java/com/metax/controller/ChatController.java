package com.metax.controller;

import com.metax.history.ChatHistoryService;
import com.metax.history.ChatHistoryType;
import com.metax.rag.etl.model.DocumentSourceType;
import com.metax.rag.indexing.DocumentIndexingRequest;
import com.metax.rag.indexing.DocumentIndexingRun;
import com.metax.rag.indexing.DocumentIndexingService;
import com.metax.rag.retrieval.RetrievalAdvisorFactory;
import com.metax.rag.retrieval.RetrievalChatResponse;
import com.metax.rag.retrieval.RetrievalFilterExpressionFactory;
import com.metax.rag.retrieval.RetrievalOptions;
import com.metax.rag.retrieval.RetrievalResponseAssembler;
import com.metax.rag.retrieval.RetrievalSearchResponse;
import com.metax.rag.retrieval.RetrievalSearchService;
import com.metax.rag.retrieval.RetrievalTrace;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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

    private final ChatHistoryService chatHistoryService;

    public ChatController(@Qualifier("chatClient") ChatClient chatClient,
                          @Qualifier("ragChatClient") ChatClient ragChatClient,
                          ChatModel chatModel,
                          VectorStore vectorStore,
                          DocumentIndexingService documentIndexingService,
                          RetrievalAdvisorFactory retrievalAdvisorFactory,
                          RetrievalFilterExpressionFactory retrievalFilterExpressionFactory,
                          RetrievalResponseAssembler retrievalResponseAssembler,
                          RetrievalSearchService retrievalSearchService,
                          ChatHistoryService chatHistoryService) {
        this.chatClient = chatClient;
        this.ragChatClient = ragChatClient;
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
        this.documentIndexingService = documentIndexingService;
        this.retrievalAdvisorFactory = retrievalAdvisorFactory;
        this.retrievalFilterExpressionFactory = retrievalFilterExpressionFactory;
        this.retrievalResponseAssembler = retrievalResponseAssembler;
        this.retrievalSearchService = retrievalSearchService;
        this.chatHistoryService = chatHistoryService;
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
            @Parameter(description = "用户消息", example = "你是谁")
            @RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return memoryChat(conversationId, msg, ChatHistoryType.CHAT);
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
    public RetrievalChatResponse ragDetails(
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
                    example = "tenantId == 't1' && knowledgeBaseId == 'kb1'")
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

        chatHistoryService.saveUserMessage(resolvedConversationId, ChatHistoryType.RAG_DETAILS, msg);
        RetrievalChatResponse response = retrievalResponseAssembler.details(requestSpec.call().chatClientResponse(),
                resolvedConversationId);
        chatHistoryService.saveAssistantMessage(resolvedConversationId, ChatHistoryType.RAG_DETAILS, response.answer());
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
                    example = "tenantId == 't1' && knowledgeBaseId == 'kb1'")
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
    private String memoryChat(String conversationId, String msg, ChatHistoryType historyType) {
        String resolvedConversationId = resolveConversationId(conversationId);

        chatHistoryService.saveUserMessage(resolvedConversationId, historyType, msg);
        String answer = chatClient.prompt()
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, resolvedConversationId))
                .user(msg)
                .call()
                .content();
        chatHistoryService.saveAssistantMessage(resolvedConversationId, historyType, answer);
        return answer;
    }

    private RetrievalChatResponse ragChat(String conversationId,
                                          String msg,
                                          RetrievalOptions options,
                                          ChatHistoryType historyType) {
        String resolvedConversationId = resolveConversationId(conversationId);
        RetrievalOptions resolvedOptions = Objects.requireNonNull(options, "RetrievalOptions must not be null");

        chatHistoryService.saveUserMessage(resolvedConversationId, historyType, msg);
        RetrievalChatResponse response = retrievalResponseAssembler.chat(ragChatClient.prompt()
                .advisors(spec -> {
                    spec.param(ChatMemory.CONVERSATION_ID, resolvedConversationId);
                    spec.advisors(retrievalAdvisorFactory.create(vectorStore, chatModel, resolvedOptions,
                            retrievalFilterExpressionFactory.create(resolvedOptions)));
                })
                .user(msg)
                .call()
                .chatClientResponse(), resolvedConversationId);
        chatHistoryService.saveAssistantMessage(resolvedConversationId, historyType, response.answer());
        return response;
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
}
