package com.metax.chat;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * MetaChatPageRequest .
 *
 * <p>
 * 聊天会话分页查询参数
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/4
 */
@Data
@Schema(description = "聊天会话分页查询参数")
public class MetaChatPageRequest {

    /**
     * 租户 ID
     */
    @NotBlank(message = "tenantId 不能为空")
    @Parameter(description = "租户 ID", example = "t1", required = true)
    @Schema(description = "租户 ID", example = "t1")
    private String tenantId;

    /**
     * 用户 ID
     */
    @NotBlank(message = "userId 不能为空")
    @Parameter(description = "用户 ID", example = "u1", required = true)
    @Schema(description = "用户 ID", example = "u1")
    private String userId;

    /**
     * 会话模式
     */
    @Parameter(description = "会话模式", example = "RAG")
    @Schema(description = "会话模式", example = "RAG")
    private String chatMode;

    /**
     * 是否收藏
     */
    @Parameter(description = "是否收藏", example = "true")
    @Schema(description = "是否收藏", example = "true")
    private Boolean favorite;

    /**
     * 是否归档
     */
    @Parameter(description = "是否归档，默认 false", example = "false")
    @Schema(description = "是否归档，默认 false", example = "false")
    private Boolean archived;

    /**
     * 页码，从 1 开始
     */
    @Min(value = 1, message = "current 不能小于 1")
    @Parameter(description = "页码，从 1 开始", example = "1")
    @Schema(description = "页码，从 1 开始", example = "1")
    private Long current;

    /**
     * 每页数量
     */
    @Min(value = 1, message = "size 不能小于 1")
    @Max(value = 100, message = "size 不能大于 100")
    @Parameter(description = "每页数量", example = "20")
    @Schema(description = "每页数量", example = "20")
    private Long size;
}
