package com.metax.retrieval.chat;

import com.metax.chat.history.MetaChatHistoryType;
import com.metax.chat.request.ChatContextScope;
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
import cn.hutool.core.date.TimeInterval;
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
        // RAG 非流式也走 fileIds 和 contextScope 解析，保证 GET / POST 与流式接口的上下文范围一致
        return chatWithRetrieval(request.getChatId(), msg, retrievalOptionsFactory.create(request, msg),
                MetaChatHistoryType.RAG, request.getFileIds(), request.getContextScope());
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
                MetaChatHistoryType.RAG, request.getFileIds(), request.getContextScope());
    }

    /**
     * 执行知识库非流式对话
     *
     * <p>
     * contextScope 是本轮回答范围的唯一协议来源，避免 RAG 模式隐式混入知识库或附件
     * FILES_ONLY 不调用知识库检索，KNOWLEDGE_ONLY 不注入附件上下文，FILES_AND_KNOWLEDGE 才同时启用两者
     *
     * @param chatId       会话 ID
     * @param msg          用户消息
     * @param options      检索参数
     * @param historyType  历史类型
     * @param fileIds      会话文件 ID 列表
     * @param contextScope 回答上下文范围
     * @return 知识库问答响应
     */
    private RetrievalChatResponse chatWithRetrieval(String chatId,
                                                    String msg,
                                                    RetrievalOptions options,
                                                    MetaChatHistoryType historyType,
                                                    List<String> fileIds,
                                                    ChatContextScope contextScope) {
        TimeInterval timer = new TimeInterval();
        // 所有知识库问答先统一会话 ID，保证 ChatMemory、完整历史和流式 meta 使用同一标识
        String resolvedChatId = chatScopeResolver.resolveChatId(chatId);
        RetrievalOptions resolvedOptions = Objects.requireNonNull(options, "RetrievalOptions must not be null");
        // 先把 HTTP 层 fileIds 解析成领域对象，后续 RAG 编排只处理 MetaContextFile
        List<MetaContextFile> files = resolveContextFiles(resolvedChatId, resolvedOptions, fileIds);
        ChatContextScope resolvedContextScope = resolveContextScope(contextScope, files);
        validateContextScope(resolvedContextScope, files);

        // 先创建会话主表并保存用户消息，后续不论是否检索都能完整回放本轮问答
        MetaChatDO chat = chatHistoryRecorder.getOrCreate(resolvedChatId, resolvedOptions.getTenantId(),
                resolvedOptions.getUserId(), historyType, msg, resolvedOptions.getKbId());

        chatHistoryRecorder.saveUserMessage(chat, historyType, msg, files);

        ChatClient.ChatClientRequestSpec requestSpec = ragChatClient.prompt()
                // RAG ChatClient 默认挂载 MessageChatMemoryAdvisor，所有分支都必须先绑定 conversationId
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, resolvedChatId));
        // contextScope 是前端和后端共同约定的协议边界，不是模型或检索决策自由判断的结果
        // Service 层必须按该范围精确组装 Advisor，避免附件上下文和知识库上下文被隐式混用
        RetrievalChatResponse response = switch (resolvedContextScope) {
            // FILES_ONLY 绝不能触发知识库检索，避免用户问附件时响应里出现知识库 references
            case FILES_ONLY -> chatWithFilesOnly(requestSpec, resolvedOptions, resolvedChatId, msg, files);
            // KNOWLEDGE_ONLY 不注入会话附件，references 只允许来自知识库 RetrievalAugmentationAdvisor
            case KNOWLEDGE_ONLY -> chatWithKnowledgeOnly(requestSpec, resolvedOptions, resolvedChatId, msg);
            // FILES_AND_KNOWLEDGE 只有用户显式选择时才允许附件和知识库同时进入 prompt
            case FILES_AND_KNOWLEDGE -> chatWithFilesAndKnowledge(requestSpec, resolvedOptions,
                    resolvedChatId, msg, files);
        };

        // 助手完整回答和知识库 references 一起归档，前端历史页不依赖 ChatMemory
        chatHistoryRecorder.saveAssistantMessage(chat, historyType, response.answer(), response.references());
        log.info("RAG 非流式问答完成：chatId = {}，tenantId = {}，kbId = {}，contextScope = {}，fileCount = {}，referenceCount = {}，durationMs = {}",
                resolvedChatId, resolvedOptions.getTenantId(), resolvedOptions.getKbId(), resolvedContextScope,
                files.size(), response.references() == null ? 0 : response.references().size(),
                timer.intervalMs());
        return response;
    }

    /**
     * 执行知识库流式对话
     *
     * <p>
     * contextScope 是本轮回答范围的唯一协议来源，流式和非流式必须得到同一套 Advisor 编排
     *
     * @param chatId       会话 ID
     * @param msg          用户消息
     * @param options      检索参数
     * @param historyType  历史类型
     * @param fileIds      会话文件 ID 列表
     * @param contextScope 回答上下文范围
     * @return SSE 流式事件
     */
    private Flux<ServerSentEvent<Object>> streamChatWithRetrieval(String chatId,
                                                                  String msg,
                                                                  RetrievalOptions options,
                                                                  MetaChatHistoryType historyType,
                                                                  List<String> fileIds,
                                                                  ChatContextScope contextScope) {
        TimeInterval timer = new TimeInterval();
        // 流式链路同样先固定 chatId，确保 meta、ChatMemory 和历史归档一致
        String resolvedChatId = chatScopeResolver.resolveChatId(chatId);
        RetrievalOptions resolvedOptions = Objects.requireNonNull(options, "RetrievalOptions must not be null");
        // 流式链路和非流式链路共用 fileIds 解析规则，避免同一请求得到不同文件集合
        List<MetaContextFile> files = resolveContextFiles(resolvedChatId, resolvedOptions, fileIds);
        ChatContextScope resolvedContextScope = resolveContextScope(contextScope, files);
        validateContextScope(resolvedContextScope, files);

        // 流式响应开始前保存用户消息，助手消息在 done 事件阶段统一保存完整文本
        MetaChatDO chat = chatHistoryRecorder.getOrCreate(resolvedChatId, resolvedOptions.getTenantId(),
                resolvedOptions.getUserId(), historyType, msg, resolvedOptions.getKbId());

        chatHistoryRecorder.saveUserMessage(chat, historyType, msg, files);

        ChatClient.ChatClientRequestSpec requestSpec = ragChatClient.prompt()
                // 流式 SKIP 且没有文件时不会进入 contextFileParams，这里统一兜住 ChatMemory conversationId
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, resolvedChatId));
        // 流式链路必须和非流式遵守同一套 contextScope 协议边界
        // includeReferences 只表示 done 事件是否读取知识库 references，不表示是否使用附件上下文
        boolean includeReferences = switch (resolvedContextScope) {
            case FILES_ONLY -> {
                // FILES_ONLY 只挂会话文件 Advisor，绝不能触发知识库检索
                requestSpec.advisors(spec -> contextFileChatSupport.contextFileParams(spec,
                        resolvedOptions.getTenantId(), resolvedOptions.getUserId(), resolvedChatId, msg, files));
                yield false;
            }
            // KNOWLEDGE_ONLY 只挂知识库检索 Advisor，不挂会话文件 Advisor
            case KNOWLEDGE_ONLY -> applyKnowledgeStreamAdvisor(requestSpec, resolvedOptions, resolvedChatId);
            // FILES_AND_KNOWLEDGE 是显式组合模式，才允许双 Advisor 同时增强 prompt
            case FILES_AND_KNOWLEDGE -> applyFilesAndKnowledgeStream(requestSpec, resolvedOptions,
                    resolvedChatId, msg, files);
        };
        requestSpec.user(msg);
        log.info("RAG 流式问答初始化完成：chatId = {}，tenantId = {}，kbId = {}，contextScope = {}，fileCount = {}，includeReferences = {}，durationMs = {}",
                resolvedChatId, resolvedOptions.getTenantId(), resolvedOptions.getKbId(), resolvedContextScope,
                files.size(), includeReferences, timer.intervalMs());

        // includeReferences 控制 done 事件是否读取检索 metadata 并归档 references
        return chatStreamEventAssembler.chatClientResponseStream(requestSpec, chat, historyType, includeReferences);
    }

    /**
     * 执行只基于附件的非流式回答
     *
     * <p>
     * FILES_ONLY 场景不允许知识库 Advisor 参与，references 必须为空，files 才是本轮来源
     *
     * @param requestSpec 请求规格
     * @param options     检索参数
     * @param chatId      会话 ID
     * @param msg         用户消息
     * @param files       本轮显式选择的会话文件
     * @return 不带知识库引用的回答
     */
    private RetrievalChatResponse chatWithFilesOnly(ChatClient.ChatClientRequestSpec requestSpec,
                                                    RetrievalOptions options,
                                                    String chatId,
                                                    String msg,
                                                    List<MetaContextFile> files) {
        // 附件回答只注入 MetaContextFileAdvisor，避免上传文件问题被知识库召回污染
        return retrievalResponseAssembler.chatWithoutReferences(requestSpec
                .advisors(spec -> contextFileChatSupport.contextFileParams(spec,
                        options.getTenantId(), options.getUserId(), chatId, msg, files))
                .user(msg)
                .call()
                .chatClientResponse(), chatId);
    }

    /**
     * 执行只基于知识库的非流式回答
     *
     * <p>
     * KNOWLEDGE_ONLY 场景不会注入会话附件，检索决策为 SKIP 时退化为无引用普通回答
     *
     * @param requestSpec 请求规格
     * @param options     检索参数
     * @param chatId      会话 ID
     * @param msg         用户消息
     * @return 知识库回答
     */
    private RetrievalChatResponse chatWithKnowledgeOnly(ChatClient.ChatClientRequestSpec requestSpec,
                                                        RetrievalOptions options,
                                                        String chatId,
                                                        String msg) {
        RetrievalDecisionResult decision = decideKnowledgeRetrieval(options, chatId);
        if (decision.decision() == RetrievalDecision.SKIP) {
            // 决策跳过知识库时不伪造 references，前端可明确知道本轮没有知识库来源
            return retrievalResponseAssembler.chatWithoutReferences(requestSpec
                    .user(msg)
                    .call()
                    .chatClientResponse(), chatId);
        }
        return retrievalResponseAssembler.chat(requestSpec
                .advisors(spec -> spec.advisors(retrievalAdvisorFactory.create(vectorStore, chatModel, options,
                        retrievalFilterExpressionFactory.create(options))))
                .user(msg)
                .call()
                .chatClientResponse(), chatId);
    }

    /**
     * 执行附件和知识库组合非流式回答
     *
     * <p>
     * 只有用户显式选择 FILES_AND_KNOWLEDGE 时才允许两种上下文同时进入模型
     *
     * @param requestSpec 请求规格
     * @param options     检索参数
     * @param chatId      会话 ID
     * @param msg         用户消息
     * @param files       本轮显式选择的会话文件
     * @return 组合上下文回答
     */
    private RetrievalChatResponse chatWithFilesAndKnowledge(ChatClient.ChatClientRequestSpec requestSpec,
                                                            RetrievalOptions options,
                                                            String chatId,
                                                            String msg,
                                                            List<MetaContextFile> files) {
        RetrievalDecisionResult decision = decideKnowledgeRetrieval(options, chatId);
        if (decision.decision() == RetrievalDecision.SKIP) {
            // 知识库被决策跳过时仍保留附件上下文，组合模式至少回答用户显式选择的文件
            return chatWithFilesOnly(requestSpec, options, chatId, msg, files);
        }
        return retrievalResponseAssembler.chat(requestSpec
                .advisors(spec -> {
                    // files 和 references 分别来自不同 metadata，响应层继续保持来源分离
                    contextFileChatSupport.contextFileParams(spec, options.getTenantId(), options.getUserId(),
                            chatId, msg, files);
                    spec.advisors(retrievalAdvisorFactory.create(vectorStore, chatModel, options,
                            retrievalFilterExpressionFactory.create(options)));
                })
                .user(msg)
                .call()
                .chatClientResponse(), chatId);
    }

    /**
     * 应用只基于知识库的流式 Advisor
     *
     * <p>
     * 该方法会修改 requestSpec，将 RetrievalAugmentationAdvisor 挂到流式请求上
     * 返回值表示 done 事件是否需要读取知识库 references
     * KNOWLEDGE_ONLY 不注入会话附件，避免附件内容无感影响知识库回答
     *
     * @param requestSpec 请求规格
     * @param options     检索参数
     * @param chatId      会话 ID
     * @return true 表示包含知识库引用
     */
    private boolean applyKnowledgeStreamAdvisor(ChatClient.ChatClientRequestSpec requestSpec,
                                                RetrievalOptions options,
                                                String chatId) {
        RetrievalDecisionResult decision = decideKnowledgeRetrieval(options, chatId);
        if (decision.decision() == RetrievalDecision.SKIP) {
            return false;
        }
        // 流式知识库回答只挂 RetrievalAugmentationAdvisor，不注入本轮附件
        requestSpec.advisors(spec -> spec.advisors(retrievalAdvisorFactory.create(vectorStore, chatModel, options,
                retrievalFilterExpressionFactory.create(options))));
        return true;
    }

    /**
     * 应用附件和知识库组合流式 Advisor
     *
     * <p>
     * 该方法会修改 requestSpec，按决策结果挂载会话文件 Advisor 和知识库检索 Advisor
     * 返回值表示 done 事件是否需要读取知识库 references
     * FILES_AND_KNOWLEDGE 是唯一允许附件和知识库同时进入 prompt 的回答范围
     *
     * @param requestSpec 请求规格
     * @param options     检索参数
     * @param chatId      会话 ID
     * @param msg         用户消息
     * @param files       本轮显式选择的会话文件
     * @return true 表示包含知识库引用
     */
    private boolean applyFilesAndKnowledgeStream(ChatClient.ChatClientRequestSpec requestSpec,
                                                 RetrievalOptions options,
                                                 String chatId,
                                                 String msg,
                                                 List<MetaContextFile> files) {
        RetrievalDecisionResult decision = decideKnowledgeRetrieval(options, chatId);
        if (decision.decision() == RetrievalDecision.SKIP) {
            // 组合模式下知识库跳过时仍回答附件，避免用户显式文件选择失效
            requestSpec.advisors(spec -> contextFileChatSupport.contextFileParams(spec,
                    options.getTenantId(), options.getUserId(), chatId, msg, files));
            return false;
        }
        requestSpec.advisors(spec -> {
            // 组合模式才允许附件和知识库同时增强 prompt
            contextFileChatSupport.contextFileParams(spec, options.getTenantId(), options.getUserId(),
                    chatId, msg, files);
            spec.advisors(retrievalAdvisorFactory.create(vectorStore, chatModel, options,
                    retrievalFilterExpressionFactory.create(options)));
        });
        return true;
    }

    /**
     * 执行知识库检索决策并记录日志
     *
     * <p>
     * FILES_ONLY 不会调用该方法，避免只问附件时触发知识库召回
     *
     * @param options 检索参数
     * @param chatId  会话 ID
     * @return 检索决策结果
     */
    private RetrievalDecisionResult decideKnowledgeRetrieval(RetrievalOptions options, String chatId) {
        // 检索决策用于短路明显不需要知识库的请求，避免无意义召回和上下文污染
        RetrievalDecisionResult decision = retrievalDecisionService.decide(options);
        log.info("RAG 检索决策：chatId = {}，decision = {}，reason = {}，query = {}",
                chatId, decision.decision(), decision.reason(), options.getQuery());
        return decision;
    }

    /**
     * 解析本轮回答上下文范围
     *
     * <p>
     * 请求未显式传入时，有文件默认只基于文件回答，无文件默认只检索知识库
     *
     * @param contextScope 请求上下文范围
     * @param files        本轮显式选择的会话文件
     * @return 实际上下文范围
     */
    private ChatContextScope resolveContextScope(ChatContextScope contextScope, List<MetaContextFile> files) {
        if (contextScope != null) {
            return contextScope;
        }
        // 默认规则必须偏向显式文件选择，避免“这个文件”类问题被知识库引用误导
        return files.isEmpty() ? ChatContextScope.KNOWLEDGE_ONLY : ChatContextScope.FILES_ONLY;
    }

    /**
     * 校验上下文范围和文件选择是否一致
     *
     * <p>
     * FILES_ONLY 和 FILES_AND_KNOWLEDGE 都必须有显式文件，避免空 fileIds 触发隐式历史文件回退
     *
     * @param contextScope 实际上下文范围
     * @param files        本轮显式选择的会话文件
     */
    private void validateContextScope(ChatContextScope contextScope, List<MetaContextFile> files) {
        if ((contextScope == ChatContextScope.FILES_ONLY || contextScope == ChatContextScope.FILES_AND_KNOWLEDGE)
                && files.isEmpty()) {
            throw new IllegalArgumentException("当前回答范围需要选择可用的会话文件");
        }
    }

    /**
     * 解析 RAG 本轮会话文件上下文
     *
     * <p>
     * fileIds 为空时不使用会话文件，非空时只返回显式指定文件
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
