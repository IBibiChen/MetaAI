package com.metax.controller.request;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * RetrievalDetailsRequest .
 *
 * <p>
 * 知识库检索调试请求参数
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@Data
@Schema(description = "知识库检索调试请求参数")
public class RetrievalDetailsRequest {

    /**
     * 会话 ID
     */
    @Parameter(description = "会话 ID，建议格式：tenantId:userId:sessionId", example = "t1:u1:s1")
    @Schema(description = "会话 ID，建议格式：tenantId:userId:sessionId", example = "t1:u1:s1")
    private String chatId;

    /**
     * 用户消息
     */
    @NotBlank(message = "msg 不能为空")
    @Parameter(description = "用户消息", example = "Spring AI 的 ETL 是什么", required = true)
    @Schema(description = "用户消息", example = "Spring AI 的 ETL 是什么")
    private String msg;

    /**
     * 租户 ID
     */
    @NotBlank(message = "tenantId 不能为空")
    @Parameter(description = "租户 ID，知识库查询强制过滤字段", example = "t1", required = true)
    @Schema(description = "租户 ID，知识库查询强制过滤字段", example = "t1")
    private String tenantId;

    /**
     * 知识库 ID
     */
    @NotBlank(message = "kbId 不能为空")
    @Parameter(description = "知识库 ID，知识库查询强制过滤字段", example = "kb1", required = true)
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
     * 检索数量
     */
    @Parameter(description = "本次检索 topK，不传时使用全局配置", example = "5")
    @Schema(description = "本次检索 topK，不传时使用全局配置", example = "5")
    private Integer topK;

    /**
     * 相似度阈值
     */
    @Parameter(description = "本次检索相似度阈值，不传时使用全局配置", example = "0.5")
    @Schema(description = "本次检索相似度阈值，不传时使用全局配置", example = "0.5")
    private Double threshold;

    /**
     * 原始过滤表达式
     */
    @Parameter(description = "原始过滤表达式，仅用于 trace 调试展示，实际检索使用结构化权限过滤",
            example = "tenantId == 't1' && kbId == 'kb1'")
    @Schema(description = "原始过滤表达式，仅用于 trace 调试展示，实际检索使用结构化权限过滤",
            example = "tenantId == 't1' && kbId == 'kb1'")
    private String filterExpression;
}
