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
import org.springframework.http.ResponseEntity;
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
     * GET 协议通过 stream query 参数控制 JSON 或 SSE 响应
     *
     * @param request 知识库问答请求参数
     * @return 知识库问答响应
     */
    @GetMapping(value = "/v1/rag", produces = {
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.TEXT_EVENT_STREAM_VALUE
    })
    @Operation(summary = "知识库问答", description = "通过 stream 参数控制普通 JSON 响应或 SSE 流式响应")
    public ResponseEntity<?> chat(@Valid @ParameterObject RetrievalChatRequest request) {
        return response(request);
    }

    /**
     * 知识库问答 JSON 请求，支持已上传聊天文件
     *
     * <p>
     * 文件必须先通过 POST /v1/chat/files 上传，再通过 fileIds 参与本轮问答
     * POST JSON 协议通过 stream 字段控制 JSON 或 SSE 响应
     *
     * @param request 知识库问答 JSON 请求参数
     * @return 知识库问答响应
     */
    @PostMapping(value = "/v1/rag", consumes = MediaType.APPLICATION_JSON_VALUE, produces = {
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.TEXT_EVENT_STREAM_VALUE
    })
    @Operation(summary = "知识库问答 JSON 请求", description = "基于 JSON 请求体和 stream 字段控制普通或流式问答")
    public ResponseEntity<?> chatJson(@Valid @RequestBody RetrievalChatRequest request) {
        return response(request);
    }

    /**
     * 根据 stream 参数组装普通或流式响应
     *
     * @param request 知识库问答请求参数
     * @return HTTP 响应
     */
    private ResponseEntity<?> response(RetrievalChatRequest request) {
        if (Boolean.TRUE.equals(request.getStream())) {
            Flux<ServerSentEvent<Object>> stream = knowledgeChatService.chatStream(request);
            return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(stream);
        }
        RetrievalChatResponse response = knowledgeChatService.chat(request);
        return ResponseEntity.ok(CommonResult.success(response));
    }
}
