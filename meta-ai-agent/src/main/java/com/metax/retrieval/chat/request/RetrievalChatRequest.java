package com.metax.retrieval.chat.request;

import com.metax.chat.request.ChatContextScope;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * RetrievalChatRequest .
 *
 * <p>
 * 知识库问答请求参数
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@Data
@Schema(description = "知识库问答请求参数")
public class RetrievalChatRequest {

    /**
     * 会话 ID
     */
    @Parameter(description = "会话 ID，建议格式：tenantId-userId-sessionId", example = "t1-u1-s1")
    @Schema(description = "会话 ID，建议格式：tenantId-userId-sessionId", example = "t1-u1-s1")
    private String chatId;

    /**
     * 用户消息
     */
    @Parameter(description = "用户消息", example = "Spring AI 的知识库问答是什么")
    @Schema(description = "用户消息", example = "Spring AI 的知识库问答是什么")
    private String msg;

    /**
     * 租户 ID
     */
    @Parameter(description = "租户 ID，知识库查询强制过滤字段", example = "t1")
    @Schema(description = "租户 ID，知识库查询强制过滤字段", example = "t1")
    private String tenantId;

    /**
     * 知识库 ID
     */
    @Parameter(description = "知识库 ID，知识库查询强制过滤字段", example = "kb1")
    @Schema(description = "知识库 ID，知识库查询强制过滤字段", example = "kb1")
    private String kbId;

    /**
     * 文档 ID
     */
    @Parameter(description = "文档 ID，可选收窄条件", example = "doc-001")
    @Schema(description = "文档 ID，可选收窄条件", example = "doc-001")
    private String documentId;

    /**
     * 文档类型
     */
    @Parameter(description = "文档类型，可选收窄条件，例如 txt、md、json、tika", example = "md")
    @Schema(description = "文档类型，可选收窄条件，例如 txt、md、json、tika", example = "md")
    private String documentType;

    /**
     * 当前用户 ID
     */
    @Parameter(description = "当前用户 ID，用于用户私有文档过滤", example = "u1")
    @Schema(description = "当前用户 ID，用于用户私有文档过滤", example = "u1")
    private String userId;

    /**
     * 当前用户可访问部门 ID 列表
     */
    @Parameter(description = "当前用户可访问部门 ID 列表，多个用英文逗号分隔", example = "d1,d2")
    @Schema(description = "当前用户可访问部门 ID 列表，多个用英文逗号分隔", example = "d1,d2")
    private String deptIds;

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
     * FILES_ONLY 只使用本轮 fileIds，KNOWLEDGE_ONLY 只检索知识库，FILES_AND_KNOWLEDGE 同时使用两者
     */
    @Parameter(description = "回答上下文范围：FILES_ONLY、KNOWLEDGE_ONLY、FILES_AND_KNOWLEDGE", example = "FILES_ONLY")
    @Schema(description = "回答上下文范围：FILES_ONLY、KNOWLEDGE_ONLY、FILES_AND_KNOWLEDGE", example = "FILES_ONLY")
    private ChatContextScope contextScope;
}
