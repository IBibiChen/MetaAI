package com.metax.chat.file;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * MetaChatFileQueryRequest .
 *
 * <p>
 * 会话文件查询请求，tenantId、userId 和 chatId 共同限定会话文件隔离边界
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/10
 */
@Setter
@Getter
@Schema(description = "会话文件查询请求")
public class MetaChatFileQueryRequest {

    /**
     * 会话 ID
     */
    @Parameter(description = "会话 ID", example = "t1-u1-s1")
    @Schema(description = "会话 ID", example = "t1-u1-s1")
    @NotBlank(message = "chatId 不能为空")
    private String chatId;

    /**
     * 租户 ID
     */
    @Parameter(description = "租户 ID", example = "t1")
    @Schema(description = "租户 ID", example = "t1")
    @NotBlank(message = "tenantId 不能为空")
    private String tenantId;

    /**
     * 用户 ID
     */
    @Parameter(description = "用户 ID", example = "u1")
    @Schema(description = "用户 ID", example = "u1")
    @NotBlank(message = "userId 不能为空")
    private String userId;
}
