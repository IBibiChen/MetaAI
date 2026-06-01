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
    public String chat(@PathVariable String provider,
                       @PathVariable String memory,
                       @RequestParam(name = "conversationId", required = false) String conversationId,
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
    public String rag(@PathVariable String provider,
                      @PathVariable String vectorStore,
                      @PathVariable String memory,
                      @RequestParam(name = "conversationId", required = false) String conversationId,
                      @RequestParam(name = "msg", defaultValue = "你是谁") String msg,
                      @RequestParam(name = "tenantId", required = false) String tenantId,
                      @RequestParam(name = "knowledgeBaseId", required = false) String knowledgeBaseId,
                      @RequestParam(name = "documentId", required = false) String documentId,
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
    public RetrievalChatResponse ragDetails(@PathVariable String provider,
                                      @PathVariable String vectorStore,
                                      @PathVariable String memory,
                                      @RequestParam(name = "conversationId", required = false) String conversationId,
                                      @RequestParam(name = "msg") String msg,
                                      @RequestParam(name = "tenantId") String tenantId,
                                      @RequestParam(name = "knowledgeBaseId") String knowledgeBaseId,
                                      @RequestParam(name = "documentId", required = false) String documentId,
                                      @RequestParam(name = "documentType", required = false) String documentType,
                                      @RequestParam(name = "topK", required = false) Integer topK,
                                      @RequestParam(name = "threshold", required = false) Double threshold,
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
     * 从 RustFS 既有对象创建 RAG 异步文档索引任务
     *
     * @param provider        embedding provider
     * @param vectorStore     向量库后端
     * @param tenantId        租户 ID
     * @param knowledgeBaseId 知识库 ID
     * @param documentId      文档 ID
     * @param documentType    文档类型
     * @param source          来源标识
     * @param bucket          RustFS bucket
     * @param objectKey       RustFS object key
     * @return 文档索引任务
     */
    @PostMapping(value = "/v1/rag/documents/import")
    public DocumentIndexingJob importRagDocument(@RequestParam(name = "provider") String provider,
                                             @RequestParam(name = "vectorStore") String vectorStore,
                                             @RequestParam(name = "tenantId") String tenantId,
                                             @RequestParam(name = "knowledgeBaseId") String knowledgeBaseId,
                                             @RequestParam(name = "documentId") String documentId,
                                             @RequestParam(name = "documentType", required = false) String documentType,
                                             @RequestParam(name = "source", required = false) String source,
                                             @RequestParam(name = "bucket") String bucket,
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
    public DocumentIndexingJob importLocalRagDocument(@RequestParam(name = "provider") String provider,
                                                  @RequestParam(name = "vectorStore") String vectorStore,
                                                  @RequestParam(name = "tenantId") String tenantId,
                                                  @RequestParam(name = "knowledgeBaseId") String knowledgeBaseId,
                                                  @RequestParam(name = "documentId") String documentId,
                                                  @RequestParam(name = "documentType", required = false) String documentType,
                                                  @RequestParam(name = "source", required = false) String source,
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
    public DocumentIndexingJob getDocumentIndexingJob(@PathVariable String jobId) {
        return documentIndexingService.getJob(jobId);
    }

    /**
     * DashScope ChatModel 直连
     *
     * @param msg 消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/dashscope/model")
    public String dashScopeModel(@RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return dashScopeChatModel.call(msg);
    }

    /**
     * OpenAI ChatModel 直连
     *
     * @param msg 消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/openai/model")
    public String openAiModel(@RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return openAiChatModel.call(msg);
    }

    /**
     * Ollama ChatModel 直连
     *
     * @param msg 消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/ollama/model")
    public String ollamaModel(@RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
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
