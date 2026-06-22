package com.metax.chat.support;

import com.metax.chat.history.MetaChatHistoryType;
import com.metax.chat.history.MetaChatHistoryDO;
import com.metax.chat.session.MetaChatDO;
import com.metax.rag.retrieval.advisor.MetaContextFile;
import com.metax.rag.retrieval.advisor.MetaContextFileKeys;
import com.metax.rag.retrieval.assembly.RetrievalResponseAssembler;
import com.metax.rag.retrieval.model.RetrievalChatResponse;
import com.metax.rag.retrieval.model.RetrievalDocumentReference;
import com.metax.rag.retrieval.stream.ChatStreamDelta;
import com.metax.rag.retrieval.stream.ChatStreamDone;
import com.metax.rag.retrieval.stream.ChatStreamError;
import com.metax.rag.retrieval.stream.ChatStreamMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ChatStreamEventAssembler .
 *
 * <p>
 * 统一组装聊天 SSE 的 meta、delta、done 和 error 事件
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatStreamEventAssembler {

    /**
     * 聊天历史归档组件
     *
     * <p>
     * SSE 流式链路只在 done 阶段写入助手完整回答，避免 delta 片段污染历史表
     */
    private final ChatHistoryRecorder chatHistoryRecorder;

    /**
     * 检索响应组装器
     *
     * <p>
     * 知识库流式问答完成后，从最后一次 ChatClientResponse 中提取 references 和 files
     */
    private final RetrievalResponseAssembler retrievalResponseAssembler;

    /**
     * 深层响应流
     *
     * <p>
     * 知识库和文件流式完成事件需要读取 Advisor 写入的 context / metadata
     * 这里必须使用 stream().chatClientResponse()，不能退化成只返回文本的 content()
     *
     * @param requestSpec       ChatClient 请求
     * @param chat              会话主表记录，提供历史归档和 SSE meta 所需 chatId
     * @param historyType       历史类型
     * @param includeReferences 是否组装知识库引用
     * @return SSE 流式事件
     */
    public Flux<ServerSentEvent<Object>> chatClientResponseStream(ChatClient.ChatClientRequestSpec requestSpec,
                                                                  MetaChatDO chat,
                                                                  MetaChatHistoryType historyType,
                                                                  boolean includeReferences) {
        // 流式响应一开始就会发送 meta 事件，所以进入响应流前必须确认会话上下文完整
        Assert.notNull(chat, "MetaChatDO must not be null");
        Assert.notNull(chat.getId(), "MetaChatDO id must not be null");
        Assert.hasText(chat.getChatId(), "MetaChatDO chatId must not be blank");
        String chatId = chat.getChatId();
        StringBuilder answer = new StringBuilder();
        AtomicReference<ChatClientResponse> lastResponse = new AtomicReference<>(ChatClientResponse.builder().build());

        // 深层响应流同样先返回 meta，后续 done 事件会复用最后一个 ChatClientResponse 的上下文
        Flux<ServerSentEvent<Object>> meta = Flux.just(event("meta", new ChatStreamMeta(chatId)));

        // 保存最后一个 ChatClientResponse，文件和知识库引用都在 Advisor 写入的 context / metadata 中
        Flux<ServerSentEvent<Object>> body = requestSpec.stream()
                .chatClientResponse()
                .doOnNext(lastResponse::set)
                .map(this::content)
                .filter(content -> content != null && !content.isEmpty())
                .doOnNext(answer::append)
                .map(content -> event("delta", new ChatStreamDelta(content)));

        // done 阶段统一组装 answer、references 和 files，并完成历史归档
        Mono<ServerSentEvent<Object>> done = Mono.fromSupplier(() -> doneEvent(chat, historyType,
                includeReferences, answer.toString(), lastResponse.get()));
        return meta.concatWith(body).concatWith(done)
                .onErrorResume(ex -> {
                    log.error("流式对话发生异常：chatId = {}", chatId, ex);
                    return Flux.just(event("error", new ChatStreamError("系统异常")));
                });
    }

    /**
     * 构造流式完成事件并归档助手完整消息
     *
     * @param chat              会话主表记录
     * @param historyType       历史类型
     * @param includeReferences 是否组装知识库引用
     * @param fullAnswer        聚合后的完整回答
     * @param lastResponse      最后一次 Spring AI 深层响应
     * @return SSE done 事件
     */
    private ServerSentEvent<Object> doneEvent(MetaChatDO chat,
                                              MetaChatHistoryType historyType,
                                              boolean includeReferences,
                                              String fullAnswer,
                                              ChatClientResponse lastResponse) {
        String chatId = chat.getChatId();
        // done 是流式响应的收口点，统一决定前端收尾数据和后端历史归档内容
        List<RetrievalDocumentReference> references = List.of();
        List<MetaContextFile> files = files(lastResponse);
        if (includeReferences) {
            // 知识库流式问答需要从最后一次响应中解析 retrieval metadata，生成轻量 references
            RetrievalChatResponse response = retrievalResponseAssembler.streamResponse(fullAnswer, lastResponse,
                    chatId);
            references = response.references();
            files = response.files();
        }

        // 历史归档保存完整 answer 和 references，delta 事件只负责前端实时展示
        MetaChatHistoryDO assistantHistory = chatHistoryRecorder.saveAssistantMessage(chat, historyType, fullAnswer,
                references);
        ChatStreamDone data = new ChatStreamDone(fullAnswer, chatId, references, files,
                assistantHistory.getCreatedAt());
        return event("done", data);
    }

    /**
     * 提取助手增量文本
     *
     * @param response Spring AI 深层响应对象
     * @return 助手文本，空响应返回 null
     */
    private String content(ChatClientResponse response) {
        // Spring AI 流式响应可能先返回 metadata 或空 result，这类事件不产生 delta
        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse == null || chatResponse.getResult() == null) {
            return null;
        }
        AssistantMessage output = chatResponse.getResult().getOutput();
        return output == null ? null : output.getText();
    }

    /**
     * 提取文件上下文来源
     *
     * @param response Spring AI 深层响应对象
     * @return 当前回答使用的会话文件列表
     */
    @SuppressWarnings("unchecked")
    private List<MetaContextFile> files(ChatClientResponse response) {
        Object value = null;
        if (response.chatResponse() != null) {
            // 优先读取 ChatResponse metadata，兼容 Advisor 在模型响应阶段写入文件来源的场景
            value = response.chatResponse().getMetadata().get(MetaContextFileKeys.CONTEXT_FILES);
        }
        if (value == null) {
            // metadata 不存在时再读取 ChatClientResponse context，兼容 Advisor 在请求上下文阶段写入
            value = response.context().get(MetaContextFileKeys.CONTEXT_FILES);
        }
        if (!(value instanceof List<?> list) || !list.stream().allMatch(MetaContextFile.class::isInstance)) {
            return List.of();
        }
        return (List<MetaContextFile>) list;
    }

    /**
     * 构造标准 SSE 事件
     *
     * @param event 事件名称
     * @param data  事件数据
     * @return SSE 事件对象
     */
    private ServerSentEvent<Object> event(String event, Object data) {
        return ServerSentEvent.builder()
                .event(event)
                .data(data)
                .build();
    }
}
