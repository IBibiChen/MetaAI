package com.metax.retrieval.chat;

import com.metax.retrieval.chat.request.RetrievalChatFileRequest;
import com.metax.retrieval.chat.request.RetrievalChatRequest;
import com.metax.rag.retrieval.model.RetrievalChatResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * KnowledgeChatController .
 *
 * <p>
 * 知识库问答和流式知识库问答接口
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "知识库问答", description = "知识库问答、会话文件增强和流式返回接口")
public class KnowledgeChatController {

    private final KnowledgeChatService knowledgeChatService;

    /**
     * 知识库问答
     *
     * <p>
     * ChatModel、EmbeddingModel 和 VectorStore 都由配置文件决定
     * 默认 ChatClient 固定使用 redisChatMemory
     *
     * @param request 知识库问答请求参数
     * @return 知识库问答响应
     */
    @GetMapping(value = "/v1/rag")
    @Operation(summary = "知识库问答", description = "使用当前配置选中的模型、记忆和知识库进行问答")
    public RetrievalChatResponse chat(@Valid @ParameterObject RetrievalChatRequest request) {
        return knowledgeChatService.chat(request);
    }

    /**
     * 知识库问答，支持聊天文件
     *
     * <p>
     * 知识库上下文由 RetrievalAugmentationAdvisor 注入，临时文件上下文由 MetaContextFileAdvisor 注入
     *
     * @param request 知识库问答文件请求参数
     * @return 知识库问答响应
     */
    @PostMapping(value = "/v1/rag", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "知识库问答，支持聊天文件", description = "同时参考知识库和本次会话上传文件进行总结、问答或对比")
    public RetrievalChatResponse chatWithFiles(@Valid @ModelAttribute RetrievalChatFileRequest request) {
        return knowledgeChatService.chatWithFiles(request);
    }

    /**
     * 知识库问答流式返回
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
    public Flux<ServerSentEvent<Object>> chatStream(@Valid @ParameterObject RetrievalChatRequest request) {
        return knowledgeChatService.chatStream(request);
    }

    /**
     * 知识库问答流式返回，支持聊天文件
     *
     * @param request 知识库问答文件请求参数
     * @return SSE 流式事件
     */
    @PostMapping(value = "/v1/rag/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "知识库问答流式返回，支持聊天文件", description = "同时参考知识库和本次会话上传文件进行流式总结、问答或对比")
    public Flux<ServerSentEvent<Object>> chatStreamWithFiles(@Valid @ModelAttribute RetrievalChatFileRequest request) {
        return knowledgeChatService.chatStreamWithFiles(request);
    }
}
