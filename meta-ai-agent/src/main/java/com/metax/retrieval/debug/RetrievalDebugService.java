package com.metax.retrieval.debug;

import com.metax.chat.history.MetaChatHistoryType;
import com.metax.chat.session.MetaChatDO;
import com.metax.chat.support.ChatHistoryRecorder;
import com.metax.chat.support.ChatScopeResolver;
import com.metax.rag.retrieval.advisor.RetrievalAdvisorFactory;
import com.metax.rag.retrieval.assembly.RetrievalResponseAssembler;
import com.metax.rag.retrieval.filter.RetrievalFilterExpressionFactory;
import com.metax.rag.retrieval.model.RetrievalChatDetailsResponse;
import com.metax.rag.retrieval.model.RetrievalOptions;
import com.metax.rag.retrieval.model.RetrievalSearchResponse;
import com.metax.rag.retrieval.search.RetrievalSearchService;
import com.metax.rag.retrieval.trace.RetrievalTrace;
import com.metax.retrieval.chat.RetrievalOptionsFactory;
import com.metax.retrieval.debug.request.RetrievalDetailsRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RetrievalDebugService .
 *
 * <p>
 * 知识库问答调试和直接检索调试编排服务
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@Service
@RequiredArgsConstructor
public class RetrievalDebugService {

    private final ChatClient ragChatClient;

    private final ChatModel chatModel;

    private final VectorStore vectorStore;

    private final RetrievalAdvisorFactory retrievalAdvisorFactory;

    private final RetrievalFilterExpressionFactory retrievalFilterExpressionFactory;

    private final RetrievalResponseAssembler retrievalResponseAssembler;

    private final RetrievalSearchService retrievalSearchService;

    private final RetrievalOptionsFactory retrievalOptionsFactory;

    private final ChatScopeResolver chatScopeResolver;

    private final ChatHistoryRecorder chatHistoryRecorder;

    /**
     * 执行知识库问答调试
     *
     * @param request 知识库检索调试请求参数
     * @return 知识库问答调试详情
     */
    public RetrievalChatDetailsResponse details(RetrievalDetailsRequest request) {
        // details 调试接口复用正式检索链路，但额外注入 traceBuilder 收集过滤、召回和后处理细节
        String resolvedChatId = chatScopeResolver.resolveChatId(request.getChatId());
        RetrievalOptions options = retrievalOptionsFactory.create(request);
        Filter.Expression filter = retrievalFilterExpressionFactory.create(options);
        RetrievalTrace.Builder traceBuilder = RetrievalTrace.builder(request.getMsg())
                .filter(String.valueOf(filter))
                .topK(request.getTopK())
                .similarityThreshold(request.getThreshold());

        ChatClient.ChatClientRequestSpec requestSpec = ragChatClient.prompt()
                .advisors(spec -> {
                    // 保持与正式问答一致的记忆窗口，同时把 traceBuilder 交给 tracing 组件写入检索细节
                    spec.param(ChatMemory.CONVERSATION_ID, resolvedChatId);
                    spec.param(RetrievalTrace.CONTEXT_KEY, traceBuilder);
                    spec.advisors(retrievalAdvisorFactory.create(vectorStore, chatModel, options, filter));
                })
                .user(request.getMsg());

        // 调试问答也进入完整历史，便于从会话页复盘本次 details 调试请求
        MetaChatDO chat = chatHistoryRecorder.getOrCreate(resolvedChatId, request.getTenantId(), request.getUserId(),
                MetaChatHistoryType.RAG_DETAILS, request.getMsg(), request.getKbId());
        chatHistoryRecorder.saveUserMessage(chat.getId(), resolvedChatId, MetaChatHistoryType.RAG_DETAILS,
                request.getMsg());
        RetrievalChatDetailsResponse response = retrievalResponseAssembler.details(requestSpec.call()
                .chatClientResponse(), resolvedChatId);
        chatHistoryRecorder.saveAssistantMessage(chat.getId(), resolvedChatId, MetaChatHistoryType.RAG_DETAILS,
                response.answer(), List.of());
        return response;
    }

    /**
     * 执行直接向量检索调试
     *
     * @param request 知识库检索调试请求参数
     * @return 直接检索响应
     */
    public RetrievalSearchResponse search(RetrievalDetailsRequest request) {
        // search 只看向量库召回结果，不调用 ChatClient、ChatModel 和 ChatMemory
        return retrievalSearchService.search(vectorStore, retrievalOptionsFactory.create(request));
    }
}
