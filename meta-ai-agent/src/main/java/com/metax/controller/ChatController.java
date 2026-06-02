package com.metax.controller;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.metax.rag.indexing.DocumentIndexingJob;
import com.metax.rag.indexing.DocumentIndexingRequest;
import com.metax.rag.indexing.DocumentIndexingService;
import com.metax.rag.etl.model.DocumentSourceType;
import com.metax.rag.core.VectorStoreRouter;
import com.metax.rag.model.EmbeddingProvider;
import com.metax.rag.model.VectorStoreBackend;
import com.metax.rag.retrieval.RetrievalAdvisorFactory;
import com.metax.rag.retrieval.RetrievalChatResponse;
import com.metax.rag.retrieval.RetrievalFilterExpressionFactory;
import com.metax.rag.retrieval.RetrievalResponseAssembler;
import com.metax.rag.retrieval.RetrievalOptions;
import com.metax.rag.retrieval.RetrievalTrace;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

/**
 * ChatController .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/28
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "智能问答与 RAG", description = "模型直连、记忆对话、RAG 检索增强和文档索引调试接口")
public class ChatController {

    private static final String DEFAULT_CONVERSATION_ID = "tenantId:userId:sessionId";

    private final Map<String, ChatClient> chatClients;

    private final DashScopeChatModel dashScopeChatModel;

    private final OpenAiChatModel openAiChatModel;

    private final OllamaChatModel ollamaChatModel;

    private final DocumentIndexingService documentIndexingService;

    private final VectorStoreRouter vectorStoreRouter;

    private final RetrievalAdvisorFactory retrievalAdvisorFactory;

    private final RetrievalFilterExpressionFactory retrievalFilterExpressionFactory;

    private final RetrievalResponseAssembler retrievalResponseAssembler;

    /**
     * 默认记忆对话
     *
     * <p>
     * 通过 provider 选择模型，通过 memory 选择对话记忆后端
     * 示例：/v1/dashscope/chat/redis 或 /v1/dashscope/chat/jdbc
     *
     * @param provider       模型 provider
     * @param memory         记忆后端
     * @param conversationId 会话 ID
     * @param msg            消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/{provider}/chat/{memory}")
    @Operation(summary = "默认记忆对话", description = "通过 provider 选择 ChatModel，通过 memory 选择 Redis 或 JDBC 对话记忆")
    public String chat(
            @Parameter(description = "模型 provider，可选值：dashscope、openai、ollama", example = "dashscope",
                    required = true, in = ParameterIn.PATH)
            @PathVariable String provider,
            @Parameter(description = "记忆后端，可选值：redis、jdbc", example = "redis", required = true,
                    in = ParameterIn.PATH)
            @PathVariable String memory,
            @Parameter(description = "会话 ID，建议格式：tenantId:userId:sessionId", example = "t1:u1:s1")
            @RequestParam(name = "conversationId", required = false) String conversationId,
            @Parameter(description = "用户消息", example = "你是谁")
            @RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return chat(resolveDefaultChatClient(provider, memory), conversationId, msg);
    }

    /**
     * RAG 检索增强对话
     *
     * <p>
     * 通过 provider 选择模型，通过 vectorStore 选择向量库，通过 memory 选择对话记忆后端
     * 示例：/v1/dashscope/rag/redis/redis 或 /v1/dashscope/rag/qdrant/jdbc
     *
     * @param provider       模型 provider
     * @param vectorStore    向量库后端
     * @param memory         记忆后端
     * @param conversationId 会话 ID
     * @param msg            消息
     * @param tenantId       租户 ID
     * @param knowledgeBaseId 知识库 ID
     * @param documentId     文档 ID
     * @param documentType   文档类型
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/{provider}/rag/{vectorStore}/{memory}")
    @Operation(summary = "RAG 检索增强对话", description = "通过 provider、vectorStore、memory 手动选择模型、向量库和记忆后端")
    public String rag(
            @Parameter(description = "模型 provider，可选值：dashscope、openai、ollama", example = "dashscope",
                    required = true, in = ParameterIn.PATH)
            @PathVariable String provider,
            @Parameter(description = "向量库后端，可选值：redis、qdrant、milvus", example = "redis",
                    required = true, in = ParameterIn.PATH)
            @PathVariable String vectorStore,
            @Parameter(description = "记忆后端，可选值：redis、jdbc", example = "redis", required = true,
                    in = ParameterIn.PATH)
            @PathVariable String memory,
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
            @RequestParam(name = "documentType", required = false) String documentType) {
        validateRetrievalScope(tenantId, knowledgeBaseId);
        RetrievalOptions options = new RetrievalOptions(tenantId, knowledgeBaseId, documentId, documentType,
                null, null, null, msg);
        return ragChat(resolveRagChatClient(provider, vectorStore, memory), provider, vectorStore,
                conversationId, msg, options);
    }

    /**
     * RAG 检索增强详情对话
     *
     * <p>
     * 返回模型回答和本次检索命中的引用来源，便于排查 topK、metadata filter 和 chunk 命中质量
     *
     * @param provider        模型 provider
     * @param vectorStore     向量库后端
     * @param memory          记忆后端
     * @param conversationId  会话 ID
     * @param msg             消息
     * @param tenantId        租户 ID
     * @param knowledgeBaseId 知识库 ID
     * @param documentId      文档 ID
     * @param documentType    文档类型
     * @param topK            检索数量
     * @param threshold       相似度阈值
     * @param filterExpression 高级过滤表达式
     * @return RAG 详情响应
     */
    @PostMapping(value = "/v1/{provider}/rag/{vectorStore}/{memory}/details")
    @Operation(summary = "RAG 检索增强详情对话", description = "返回 answer、references 和 trace，用于调试召回质量、过滤条件和后处理效果")
    public RetrievalChatResponse ragDetails(
            @Parameter(description = "模型 provider，可选值：dashscope、openai、ollama", example = "dashscope",
                    required = true, in = ParameterIn.PATH)
            @PathVariable String provider,
            @Parameter(description = "向量库后端，可选值：redis、qdrant、milvus", example = "redis",
                    required = true, in = ParameterIn.PATH)
            @PathVariable String vectorStore,
            @Parameter(description = "记忆后端，可选值：redis、jdbc", example = "redis", required = true,
                    in = ParameterIn.PATH)
            @PathVariable String memory,
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
            @Parameter(description = "本次检索 topK，不传时使用全局配置", example = "5")
            @RequestParam(name = "topK", required = false) Integer topK,
            @Parameter(description = "本次检索相似度阈值，不传时使用全局配置", example = "0.5")
            @RequestParam(name = "threshold", required = false) Double threshold,
            @Parameter(description = "高级过滤表达式，仅建议调试使用", example = "tenantId == 't1' && knowledgeBaseId == 'kb1'")
            @RequestParam(name = "filterExpression", required = false) String filterExpression) {
        String resolvedConversationId = resolveConversationId(conversationId);
        validateRetrievalScope(tenantId, knowledgeBaseId);
        RetrievalOptions options = new RetrievalOptions(tenantId, knowledgeBaseId, documentId, documentType,
                topK, threshold, filterExpression, msg);
        Filter.Expression filter = retrievalFilterExpressionFactory.create(options);
        RetrievalTrace.Builder traceBuilder = RetrievalTrace.builder(msg)
                .filter(filterExpression != null && !filterExpression.isBlank() ? filterExpression : String.valueOf(filter))
                .topK(topK)
                .similarityThreshold(threshold);

        ChatClient.ChatClientRequestSpec requestSpec = resolveRagChatClient(provider, vectorStore, memory).prompt()
                .advisors(spec -> {
                    spec.param(ChatMemory.CONVERSATION_ID, resolvedConversationId);
                    spec.param(RetrievalTrace.CONTEXT_KEY, traceBuilder);
                    spec.advisors(retrievalAdvisorFactory.create(resolveVectorStore(provider, vectorStore),
                            resolveChatModel(provider), options, filter));
                    if (filterExpression != null && !filterExpression.isBlank()) {
                        spec.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION, filterExpression);
                    }
                })
                .user(msg);

        return retrievalResponseAssembler.details(requestSpec.call().chatClientResponse(), resolvedConversationId);
    }

    /**
     * 从对象存储既有对象创建 RAG 异步文档索引任务
     *
     * @param provider        embedding provider
     * @param vectorStore     向量库后端
     * @param tenantId        租户 ID
     * @param knowledgeBaseId 知识库 ID
     * @param documentId      文档 ID
     * @param documentType    文档类型
     * @param source          来源标识
     * @param bucket          对象存储 bucket
     * @param objectKey       对象存储 object key
     * @return 文档索引任务
     */
    @PostMapping(value = "/v1/rag/documents/import")
    @Operation(summary = "从对象存储创建 RAG 文档索引任务", description = "原始文件必须已经归档到对象存储，本接口只创建异步 ETL 索引任务")
    public DocumentIndexingJob importRagDocument(
            @Parameter(description = "embedding provider，可选值：dashscope、openai、ollama", example = "dashscope",
                    required = true)
            @RequestParam(name = "provider") String provider,
            @Parameter(description = "向量库后端，可选值：redis、qdrant、milvus", example = "redis", required = true)
            @RequestParam(name = "vectorStore") String vectorStore,
            @Parameter(description = "租户 ID", example = "t1", required = true)
            @RequestParam(name = "tenantId") String tenantId,
            @Parameter(description = "知识库 ID", example = "kb1", required = true)
            @RequestParam(name = "knowledgeBaseId") String knowledgeBaseId,
            @Parameter(description = "文档 ID，同一 documentId 重复导入会覆盖旧 chunk", example = "doc-001",
                    required = true)
            @RequestParam(name = "documentId") String documentId,
            @Parameter(description = "文档类型，可为空，为空时根据 objectKey 后缀识别", example = "md")
            @RequestParam(name = "documentType", required = false) String documentType,
            @Parameter(description = "来源标识，可用于前端展示引用来源", example = "knowledge/t1/kb1/demo.md")
            @RequestParam(name = "source", required = false) String source,
            @Parameter(description = "对象存储 bucket", example = "meta-ai-knowledge", required = true)
            @RequestParam(name = "bucket") String bucket,
            @Parameter(description = "对象存储 object key", example = "knowledge/t1/kb1/demo.md", required = true)
            @RequestParam(name = "objectKey") String objectKey) {
        return documentIndexingService.submit(new DocumentIndexingRequest(EmbeddingProvider.from(provider),
                VectorStoreBackend.from(vectorStore), tenantId, knowledgeBaseId, documentId, documentType,
                DocumentSourceType.OBJECT_STORAGE, source, bucket, objectKey, null));
    }

    /**
     * 从受控本地目录创建 RAG 异步文档索引任务
     *
     * @param provider        embedding provider
     * @param vectorStore     向量库后端
     * @param tenantId        租户 ID
     * @param knowledgeBaseId 知识库 ID
     * @param documentId      文档 ID
     * @param documentType    文档类型
     * @param source          来源标识
     * @param path            本地文件相对路径
     * @return 文档索引任务
     */
    @PostMapping(value = "/v1/rag/documents/import/local")
    @Operation(summary = "从受控本地目录创建 RAG 文档索引任务", description = "path 必须是 metax.ai.rag.storage.local-root 下的相对路径")
    public DocumentIndexingJob importLocalRagDocument(
            @Parameter(description = "embedding provider，可选值：dashscope、openai、ollama", example = "dashscope",
                    required = true)
            @RequestParam(name = "provider") String provider,
            @Parameter(description = "向量库后端，可选值：redis、qdrant、milvus", example = "redis", required = true)
            @RequestParam(name = "vectorStore") String vectorStore,
            @Parameter(description = "租户 ID", example = "t1", required = true)
            @RequestParam(name = "tenantId") String tenantId,
            @Parameter(description = "知识库 ID", example = "kb1", required = true)
            @RequestParam(name = "knowledgeBaseId") String knowledgeBaseId,
            @Parameter(description = "文档 ID，同一 documentId 重复导入会覆盖旧 chunk", example = "doc-001",
                    required = true)
            @RequestParam(name = "documentId") String documentId,
            @Parameter(description = "文档类型，可为空，为空时根据 path 后缀识别", example = "md")
            @RequestParam(name = "documentType", required = false) String documentType,
            @Parameter(description = "来源标识，可用于前端展示引用来源", example = "local/demo.md")
            @RequestParam(name = "source", required = false) String source,
            @Parameter(description = "本地知识库文件相对路径", example = "demo.md", required = true)
            @RequestParam(name = "path") String path) {
        return documentIndexingService.importLocalFile(provider, vectorStore, tenantId, knowledgeBaseId,
                documentId, documentType, path, source);
    }

    /**
     * 查询 RAG 异步文档索引任务
     *
     * @param jobId 任务 ID
     * @return 文档索引任务
     */
    @GetMapping(value = "/v1/rag/documents/jobs/{jobId}")
    @Operation(summary = "查询 RAG 文档索引任务", description = "根据 jobId 查询异步索引任务状态、写入 chunk 数和失败原因")
    public DocumentIndexingJob getDocumentIndexingJob(
            @Parameter(description = "文档索引任务 ID", example = "c2a6bb6d-b0e6-4c40-9f32-3b08b5b19d62",
                    required = true, in = ParameterIn.PATH)
            @PathVariable String jobId) {
        return documentIndexingService.getJob(jobId);
    }

    /**
     * DashScope ChatModel 直连
     *
     * @param msg 消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/dashscope/model")
    @Operation(summary = "DashScope ChatModel 直连", description = "绕过 ChatClient 和 ChatMemory，直接调用 DashScopeChatModel")
    public String dashScopeModel(
            @Parameter(description = "用户消息", example = "你是谁")
            @RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return dashScopeChatModel.call(msg);
    }

    /**
     * OpenAI ChatModel 直连
     *
     * @param msg 消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/openai/model")
    @Operation(summary = "OpenAI 兼容 ChatModel 直连", description = "绕过 ChatClient 和 ChatMemory，直接调用 OpenAiChatModel")
    public String openAiModel(
            @Parameter(description = "用户消息", example = "你是谁")
            @RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return openAiChatModel.call(msg);
    }

    /**
     * Ollama ChatModel 直连
     *
     * @param msg 消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/ollama/model")
    @Operation(summary = "Ollama ChatModel 直连", description = "绕过 ChatClient 和 ChatMemory，直接调用 OllamaChatModel")
    public String ollamaModel(
            @Parameter(description = "用户消息", example = "你是谁")
            @RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return ollamaChatModel.call(msg);
    }

    /**
     * 使用预配置 ChatClient 对话
     *
     * @param chatClient     ChatClient
     * @param conversationId 会话 ID
     * @param msg            消息
     * @return 模型响应内容
     */
    private String chat(ChatClient chatClient, String conversationId, String msg) {
        String resolvedConversationId = resolveConversationId(conversationId);

        return chatClient.prompt()
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, resolvedConversationId))
                .user(msg)
                .call()
                .content();
    }

    private String ragChat(ChatClient chatClient,
                           String provider,
                           String vectorStore,
                           String conversationId,
                           String msg,
                           RetrievalOptions options) {
        String resolvedConversationId = resolveConversationId(conversationId);
        VectorStore resolvedVectorStore = resolveVectorStore(provider, vectorStore);
        RetrievalOptions resolvedOptions = Objects.requireNonNull(options, "RetrievalOptions must not be null");

        return chatClient.prompt()
                .advisors(spec -> {
                    spec.param(ChatMemory.CONVERSATION_ID, resolvedConversationId);
                    spec.advisors(retrievalAdvisorFactory.create(resolvedVectorStore, resolveChatModel(provider),
                            resolvedOptions, retrievalFilterExpressionFactory.create(resolvedOptions)));
                })
                .user(msg)
                .call()
                .content();
    }

    private VectorStore resolveVectorStore(String provider, String vectorStore) {
        return vectorStoreRouter.getVectorStore(EmbeddingProvider.from(provider), VectorStoreBackend.from(vectorStore));
    }

    private ChatModel resolveChatModel(String provider) {
        return switch (provider.toLowerCase()) {
            case "dashscope" -> dashScopeChatModel;
            case "openai" -> openAiChatModel;
            case "ollama" -> ollamaChatModel;
            default -> throw new IllegalArgumentException("Unsupported provider: " + provider);
        };
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

    /**
     * 解析默认对话 ChatClient
     *
     * @param provider 模型 provider
     * @param memory   记忆后端
     * @return ChatClient
     */
    private ChatClient resolveDefaultChatClient(String provider, String memory) {
        String beanName = providerPrefix(provider) + memoryPrefix(memory) + "ChatClient";
        return getChatClient(beanName);
    }

    /**
     * 解析 RAG 检索增强 ChatClient
     *
     * @param provider    模型 provider
     * @param vectorStore 向量库后端
     * @param memory      记忆后端
     * @return ChatClient
     */
    private ChatClient resolveRagChatClient(String provider, String vectorStore, String memory) {
        String beanName = providerPrefix(provider) + memoryPrefix(memory) + vectorStorePrefix(vectorStore) + "RagChatClient";
        return getChatClient(beanName);
    }

    /**
     * 获取 ChatClient Bean
     *
     * @param beanName Bean 名称
     * @return ChatClient
     */
    private ChatClient getChatClient(String beanName) {
        ChatClient chatClient = chatClients.get(beanName);
        if (chatClient == null) {
            throw new IllegalArgumentException("Unsupported ChatClient bean: " + beanName);
        }
        return chatClient;
    }

    /**
     * provider 参数转 Bean 名前缀
     *
     * @param provider 模型 provider
     * @return Bean 名前缀
     */
    private String providerPrefix(String provider) {
        return switch (provider.toLowerCase()) {
            case "dashscope" -> "dashScope";
            case "openai" -> "openAi";
            case "ollama" -> "ollama";
            default -> throw new IllegalArgumentException("Unsupported provider: " + provider);
        };
    }

    /**
     * memory 参数转 Bean 名片段
     *
     * @param memory 记忆后端
     * @return Bean 名片段
     */
    private String memoryPrefix(String memory) {
        return switch (memory.toLowerCase()) {
            case "redis" -> "Redis";
            case "jdbc" -> "Jdbc";
            default -> throw new IllegalArgumentException("Unsupported memory: " + memory);
        };
    }

    /**
     * vectorStore 参数转 Bean 名片段
     *
     * @param vectorStore 向量库后端
     * @return Bean 名片段
     */
    private String vectorStorePrefix(String vectorStore) {
        return switch (vectorStore.toLowerCase()) {
            case "redis" -> "MemoryRedis";
            case "qdrant" -> "MemoryQdrant";
            case "milvus" -> "MemoryMilvus";
            default -> throw new IllegalArgumentException("Unsupported vectorStore: " + vectorStore);
        };
    }

}
