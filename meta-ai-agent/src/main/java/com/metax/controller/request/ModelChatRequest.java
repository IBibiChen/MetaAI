package com.metax.controller.request;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * ModelChatRequest .
 *
 * <p>
 * 模型直连请求参数
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@Data
@Schema(description = "模型直连请求参数")
public class ModelChatRequest {

    /**
     * 用户消息
     */
    @Parameter(description = "用户消息", example = "你是谁")
    @Schema(description = "用户消息", example = "你是谁")
    private String msg;
}
