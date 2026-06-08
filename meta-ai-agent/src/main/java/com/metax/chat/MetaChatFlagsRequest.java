package com.metax.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * MetaChatFlagsRequest .
 *
 * <p>
 * 聊天会话状态更新请求
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@Data
@Schema(description = "聊天会话状态更新请求")
public class MetaChatFlagsRequest {

    /**
     * 是否置顶
     */
    @Schema(description = "是否置顶", example = "true")
    private Boolean pinned;

    /**
     * 是否收藏
     */
    @Schema(description = "是否收藏", example = "true")
    private Boolean favorite;

    /**
     * 是否归档
     */
    @Schema(description = "是否归档", example = "false")
    private Boolean archived;
}
