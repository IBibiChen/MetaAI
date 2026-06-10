package com.metax.chat.request;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * ChatRequest .
 *
 * <p>
 * 记忆对话请求参数
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@Data
@Schema(description = "记忆对话请求参数")
public class ChatRequest {

    /**
     * 会话 ID
     */
    @Parameter(description = "会话 ID，建议格式：tenantId-userId-sessionId", example = "t1-u1-s1")
    @Schema(description = "会话 ID，建议格式：tenantId-userId-sessionId", example = "t1-u1-s1")
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
    @Parameter(description = "用户消息", example = "你是谁")
    @Schema(description = "用户消息", example = "你是谁")
    private String msg;

    /**
     * 是否启用流式响应
     *
     * <p>
     * GET 协议通过 query 参数传入，POST JSON 协议通过请求体传入
     */
    @Parameter(description = "是否启用流式响应，true 表示返回 SSE", example = "false")
    @Schema(description = "是否启用流式响应，true 表示返回 SSE", example = "false")
    private Boolean stream;

    /**
     * 会话文件 ID 列表
     *
     * <p>
     * 为空时不使用会话文件，非空时只使用显式指定文件
     */
    @Parameter(description = "会话文件 ID 列表，空值表示本轮不使用会话文件", example = "2063846120613888002")
    @Schema(description = "会话文件 ID 列表，空值表示本轮不使用会话文件", example = "[\"2063846120613888002\"]")
    private List<String> fileIds;

    /**
     * 回答上下文范围
     *
     * <p>
     * 普通聊天只使用 FILES_ONLY 语义，RAG 问答会根据该字段决定是否启用知识库检索
     */
    @Parameter(description = "回答上下文范围：FILES_ONLY、KNOWLEDGE_ONLY、FILES_AND_KNOWLEDGE", example = "FILES_ONLY")
    @Schema(description = "回答上下文范围：FILES_ONLY、KNOWLEDGE_ONLY、FILES_AND_KNOWLEDGE", example = "FILES_ONLY")
    private ChatContextScope contextScope;
}
