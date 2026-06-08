package com.metax.chat;

import com.metax.chat.file.MetaChatFileResponse;
import com.metax.chat.request.ChatFileRequest;
import com.metax.chat.request.ChatRequest;
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
     *
     * @param request 记忆对话请求参数
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/chat")
    @Operation(summary = "默认记忆对话", description = "使用当前配置选中的 ChatModel 和 ChatMemory 进行多轮对话")
    public String chat(@Valid @ParameterObject ChatRequest request) {
        return chatMessageService.chat(request);
    }

    /**
     * 默认记忆对话，支持聊天文件
     *
     * <p>
     * multipart 入口只用于携带会话级文件
     * 文件只绑定当前 chatId，不进入知识库，也不会被 /v1/rag 检索
     *
     * @param request 记忆对话文件请求参数
     * @return 文件上下文对话响应
     */
    @PostMapping(value = "/v1/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "默认记忆对话，支持聊天文件", description = "在现有聊天入口中上传文件并基于文件内容总结或问答")
    public MetaChatFileResponse chatWithFiles(@Valid @ModelAttribute ChatFileRequest request) {
        return chatMessageService.chatWithFiles(request);
    }

    /**
     * 默认记忆对话流式返回
     *
     * <p>
     * 使用 SSE 返回 meta、delta、done 和 error 事件
     * 模型完整回答会在流结束后写入完整聊天历史
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
     * 默认记忆对话流式返回，支持聊天文件
     *
     * <p>
     * multipart 入口用于上传会话级临时文件，文件上下文由 MetaContextFileAdvisor 注入
     *
     * @param request 记忆对话文件请求参数
     * @return SSE 流式事件
     */
    @PostMapping(value = "/v1/chat/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "默认记忆对话流式返回，支持聊天文件", description = "上传临时文件并基于文件内容进行流式总结或问答")
    public Flux<ServerSentEvent<Object>> chatStreamWithFiles(@Valid @ModelAttribute ChatFileRequest request) {
        return chatMessageService.chatStreamWithFiles(request);
    }
}
