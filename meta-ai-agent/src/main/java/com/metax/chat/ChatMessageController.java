package com.metax.chat;

import com.metax.chat.request.ChatRequest;
import com.metax.chat.response.ChatMessageResponse;
import com.metax.common.CommonResult;
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
 * ChatMessageController .
 *
 * <p>
 * 普通记忆对话和会话文件对话接口
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "记忆对话", description = "普通记忆对话、会话文件对话和流式返回接口")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    /**
     * 默认记忆对话
     *
     * <p>
     * 模型 provider 由 spring.ai.model.chat 配置决定
     * 默认记忆后端固定使用 redisChatMemory
     * GET 协议用于简单 query 参数调用，也方便浏览器和接口文档直接调试
     *
     * @param request 记忆对话请求参数
     * @return 记忆对话响应
     */
    @GetMapping(value = "/v1/chat")
    @Operation(summary = "默认记忆对话", description = "使用当前配置选中的 ChatModel 和 ChatMemory 进行多轮对话")
    public CommonResult<ChatMessageResponse> chat(@Valid @ParameterObject ChatRequest request) {
        return CommonResult.success(chatMessageService.chat(request));
    }

    /**
     * 默认记忆对话 JSON 请求，支持已上传聊天文件
     *
     * <p>
     * 文件必须先通过 POST /v1/chat/files 上传，再通过 fileIds 参与本轮问答
     * POST JSON 协议用于携带复杂请求体和 Authorization Header
     *
     * @param request 记忆对话 JSON 请求参数
     * @return 记忆对话响应
     */
    @PostMapping(value = "/v1/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "默认记忆对话 JSON 请求", description = "基于 JSON 请求体和已上传会话文件进行问答")
    public CommonResult<ChatMessageResponse> chatJson(@Valid @RequestBody ChatRequest request) {
        return CommonResult.success(chatMessageService.chat(request));
    }

    /**
     * 默认记忆对话流式返回
     *
     * <p>
     * 使用 SSE 返回 meta、delta、done 和 error 事件
     * 模型完整回答会在流结束后写入完整聊天历史
     * GET 流式协议保留给原生 EventSource，参数全部来自 query string
     *
     * @param request 记忆对话请求参数
     * @return SSE 流式事件
     */
    @GetMapping(value = "/v1/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "默认记忆对话流式返回", description = "使用当前配置选中的 ChatModel 和 ChatMemory 进行多轮流式对话")
    public Flux<ServerSentEvent<Object>> chatStream(@Valid @ParameterObject ChatRequest request) {
        return chatMessageService.chatStream(request);
    }

    /**
     * 默认记忆对话 JSON 流式返回，支持已上传聊天文件
     *
     * <p>
     * 文件必须先通过 POST /v1/chat/files 上传，再通过 fileIds 参与本轮流式问答
     * POST JSON 流式协议由 fetchEventSource 调用，支持 Authorization Header 和复杂参数
     *
     * @param request 记忆对话 JSON 流式请求参数
     * @return SSE 流式事件
     */
    @PostMapping(value = "/v1/chat/stream", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "默认记忆对话 JSON 流式返回", description = "基于 JSON 请求体和已上传会话文件进行流式问答")
    public Flux<ServerSentEvent<Object>> chatStreamJson(@Valid @RequestBody ChatRequest request) {
        return chatMessageService.chatStream(request);
    }
}
