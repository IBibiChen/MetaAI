package com.metax.chat.file;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

/**
 * MetaChatFileUploadRequest .
 *
 * <p>
 * 会话文件上传请求，上传接口是聊天文件唯一保留 multipart/form-data 的入口
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/10
 */
@Setter
@Getter
@Schema(description = "会话文件上传请求")
public class MetaChatFileUploadRequest {

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

    /**
     * 聊天文件
     */
    @Parameter(description = "聊天文件")
    @ArraySchema(schema = @Schema(description = "聊天文件", type = "string", format = "binary"))
    private MultipartFile[] files;
}
