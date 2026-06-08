package com.metax.chat;

import com.metax.chat.request.ModelChatRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ModelDebugController .
 *
 * <p>
 * 当前 ChatModel 直连调试接口
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "模型调试", description = "当前 ChatModel 直连调试接口")
public class ModelDebugController {

    private final ModelDebugService modelDebugService;

    /**
     * 当前 ChatModel 直连
     *
     * @param request 模型直连请求参数
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/model")
    @Operation(summary = "当前 ChatModel 直连", description = "绕过 ChatClient 和 ChatMemory，直接调用当前配置选中的 ChatModel")
    public String chat(@Valid @ParameterObject ModelChatRequest request) {
        return modelDebugService.chat(request);
    }
}
