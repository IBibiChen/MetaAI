package com.metax.retrieval.chat.request;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * RetrievalChatFileRequest .
 *
 * <p>
 * 知识库问答文件请求参数
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@Data
@Schema(description = "知识库问答文件请求参数")
public class RetrievalChatFileRequest {

    /**
     * 会话 ID
     */
    @Parameter(description = "会话 ID，建议格式：tenantId:userId:sessionId", example = "t1:u1:s1")
    @Schema(description = "会话 ID，建议格式：tenantId:userId:sessionId", example = "t1:u1:s1")
    private String chatId;

    /**
     * 用户消息
     */
    @Parameter(description = "用户消息", example = "对比知识库方案和上传文件")
    @Schema(description = "用户消息", example = "对比知识库方案和上传文件")
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
     * 聊天文件
     */
    @Parameter(description = "聊天文件")
    @ArraySchema(schema = @Schema(description = "聊天文件", type = "string", format = "binary"))
    private MultipartFile[] files;
}
