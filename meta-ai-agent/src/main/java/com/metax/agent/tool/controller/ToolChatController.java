package com.metax.agent.tool.controller;

import com.metax.agent.tool.model.ToolChatRequest;
import com.metax.agent.tool.model.ToolChatResponse;
import com.metax.agent.tool.service.ToolChatService;
import com.metax.common.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * ToolChatController .
 *
 * <p>
 * 请求级显式工具对话接口
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "工具对话", description = "请求级显式工具调用对话接口")
public class ToolChatController {

    private final ToolChatService toolChatService;

    /**
     * 请求级显式工具对话
     *
     * <p>
     * GET 协议用于快速验证 Spring AI ToolCallAdvisor、toolCallbacks 和 toolContext 请求级链路
     *
     * @param request 请求级显式工具对话参数
     * @return 请求级显式工具对话响应
     */
    @GetMapping(value = "/v1/chat/tools", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "请求级显式工具对话", description = "通过服务端 allowlist 在本轮请求显式暴露工具")
    public ResponseEntity<CommonResult<ToolChatResponse>> chat(@Valid @ParameterObject ToolChatRequest request) {
        return response(request);
    }

    /**
     * 请求级显式工具对话 JSON 请求
     *
     * <p>
     * POST JSON 协议用于前端按请求体传入本轮允许的 toolNames
     *
     * @param request 请求级显式工具对话 JSON 参数
     * @return 请求级显式工具对话响应
     */
    @PostMapping(value = "/v1/chat/tools", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "请求级显式工具对话 JSON 请求", description = "基于 JSON 请求体显式选择本轮允许的工具")
    public ResponseEntity<CommonResult<ToolChatResponse>> chatJson(@Valid @RequestBody ToolChatRequest request) {
        return response(request);
    }

    private ResponseEntity<CommonResult<ToolChatResponse>> response(ToolChatRequest request) {
        return ResponseEntity.ok(CommonResult.success(toolChatService.chat(request)));
    }
}
