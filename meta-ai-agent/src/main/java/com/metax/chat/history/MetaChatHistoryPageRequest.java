package com.metax.chat.history;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * MetaChatHistoryPageRequest .
 *
 * <p>
 * 完整聊天历史分页查询参数
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
@Data
@Schema(description = "完整聊天历史分页查询参数")
public class MetaChatHistoryPageRequest {

    /**
     * 会话 ID
     */
    @Parameter(description = "会话 ID，建议格式：tenantId-userId-sessionId", example = "t1-u1-s1", required = true)
    @Schema(description = "会话 ID，建议格式：tenantId-userId-sessionId", example = "t1-u1-s1")
    private String chatId;

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
    @Max(value = 500, message = "size 不能大于 500")
    @Parameter(description = "每页数量", example = "20")
    @Schema(description = "每页数量", example = "20")
    private Long size;
}
