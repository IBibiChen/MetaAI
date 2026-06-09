package com.metax.retrieval.chat;

import com.metax.common.CommonResult;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
     * GET 协议用于简单检索参数和接口调试，复杂上下文建议使用 POST JSON
     *
     * @param request 知识库问答请求参数
     * @return 知识库问答响应
     */
    @GetMapping(value = "/v1/rag")
    @Operation(summary = "知识库问答", description = "使用当前配置选中的模型、记忆和知识库进行问答")
    public CommonResult<RetrievalChatResponse> chat(@Valid @ParameterObject RetrievalChatRequest request) {
        return CommonResult.success(knowledgeChatService.chat(request));
    }

    /**
     * 知识库问答 JSON 请求，支持已上传聊天文件
     *
     * <p>
     * 文件必须先通过 POST /v1/chat/files 上传，再通过 fileIds 参与本轮问答
     * POST JSON 协议用于携带复杂检索范围、fileIds 和 Authorization Header
     *
     * @param request 知识库问答 JSON 请求参数
     * @return 知识库问答响应
     */
    @PostMapping(value = "/v1/rag", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "知识库问答 JSON 请求", description = "基于 JSON 请求体同时参考知识库和已上传会话文件进行问答")
    public CommonResult<RetrievalChatResponse> chatJson(@Valid @RequestBody RetrievalChatRequest request) {
        return CommonResult.success(knowledgeChatService.chat(request));
    }

    /**
     * 知识库问答流式返回
     *
     * <p>
     * 使用 SSE 返回 meta、delta、done 和 error 事件
     * done 事件中返回完整 answer、chatId 和轻量 references
     * GET 流式协议保留给浏览器原生 EventSource，参数通过 query string 传入
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
     * 知识库问答 JSON 流式返回，支持已上传聊天文件
     *
     * <p>
     * 文件必须先通过 POST /v1/chat/files 上传，再通过 fileIds 参与本轮流式问答
     * POST JSON 流式协议用于复杂检索范围和鉴权场景，前端通过 fetchEventSource 消费
     *
     * @param request 知识库问答 JSON 流式请求参数
     * @return SSE 流式事件
     */
    @PostMapping(value = "/v1/rag/stream", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "知识库问答 JSON 流式返回", description = "同时参考知识库和已上传会话文件进行流式总结、问答或对比")
    public Flux<ServerSentEvent<Object>> chatStreamJson(@Valid @RequestBody RetrievalChatRequest request) {
        return knowledgeChatService.chatStream(request);
    }
}
