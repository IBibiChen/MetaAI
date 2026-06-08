package com.metax.chat.session;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * MetaChatRenameRequest .
 *
 * <p>
 * 聊天会话重命名请求
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@Data
@Schema(description = "聊天会话重命名请求")
public class MetaChatRenameRequest {

    /**
     * 会话标题
     */
    @NotBlank(message = "title 不能为空")
    @Size(max = 255, message = "title 不能超过 255 个字符")
    @Schema(description = "会话标题", example = "知识库总结")
    private String title;
}
