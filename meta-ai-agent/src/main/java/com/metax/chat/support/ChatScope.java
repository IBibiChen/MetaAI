package com.metax.chat.support;

/**
 * ChatScope .
 *
 * <p>
 * 聊天请求解析后的租户和用户范围
 *
 * @param tenantId 租户 ID
 * @param userId   用户 ID
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
public record ChatScope(
        String tenantId,
        String userId
) {
}
