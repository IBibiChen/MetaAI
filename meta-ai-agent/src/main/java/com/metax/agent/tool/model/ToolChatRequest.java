package com.metax.agent.tool.model;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * ToolChatRequest .
 *
 * <p>
 * 请求级显式工具对话请求参数
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
@Data
@Schema(description = "请求级显式工具对话请求参数")
public class ToolChatRequest {

    /**
     * 会话 ID
     */
    @Parameter(description = "会话 ID，建议格式：tenantId-userId-sessionId", example = "t1-u1-tool-s1")
    @Schema(description = "会话 ID，建议格式：tenantId-userId-sessionId", example = "t1-u1-tool-s1")
    private String chatId;

    /**
     * 租户 ID
     */
    @Parameter(description = "租户 ID", example = "t1")
    @Schema(description = "租户 ID", example = "t1")
    private String tenantId;

    /**
     * 用户 ID
     */
    @Parameter(description = "用户 ID", example = "u1")
    @Schema(description = "用户 ID", example = "u1")
    private String userId;

    /**
     * 用户消息
     */
    @Parameter(description = "用户消息", example = "现在几点")
    @Schema(description = "用户消息", example = "现在几点")
    private String msg;

    /**
     * 本轮允许使用的工具名称
     *
     * <p>
     * 为空时使用服务端请求级工具 allowlist，非空时必须全部命中服务端 allowlist
     */
    @Parameter(description = "本轮允许使用的工具名称，空值表示使用服务端 allowlist", example = "currentDateTime")
    @Schema(description = "本轮允许使用的工具名称，空值表示使用服务端 allowlist", example = "[\"currentDateTime\"]")
    private List<String> toolNames;
}
