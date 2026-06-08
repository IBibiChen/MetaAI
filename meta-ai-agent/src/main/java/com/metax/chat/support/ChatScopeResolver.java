package com.metax.chat.support;

import org.springframework.stereotype.Component;

/**
 * ChatScopeResolver .
 *
 * <p>
 * 统一解析 chatId、tenantId 和 userId 的兜底关系
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@Component
public class ChatScopeResolver {

    /**
     * 解析会话 ID
     *
     * @param chatId 原始会话 ID
     * @return 兜底后的会话 ID
     */
    public String resolveChatId(String chatId) {
        return chatId == null || chatId.isBlank() ? ChatDefaults.CHAT_ID : chatId;
    }

    /**
     * 解析租户和用户范围
     *
     * <p>
     * tenantId 或 userId 为空时优先从 chatId 的 tenantId:userId:sessionId 结构中提取
     *
     * @param chatId   会话 ID
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return 聊天范围
     */
    public ChatScope resolve(String chatId, String tenantId, String userId) {
        String resolvedTenantId = tenantId;
        String resolvedUserId = userId;
        if ((resolvedTenantId == null || resolvedTenantId.isBlank())
                || (resolvedUserId == null || resolvedUserId.isBlank())) {
            String[] parts = chatId == null ? new String[0] : chatId.split(":");
            if ((resolvedTenantId == null || resolvedTenantId.isBlank()) && parts.length > 0) {
                resolvedTenantId = parts[0];
            }
            if ((resolvedUserId == null || resolvedUserId.isBlank()) && parts.length > 1) {
                resolvedUserId = parts[1];
            }
        }
        if (resolvedTenantId == null || resolvedTenantId.isBlank()) {
            resolvedTenantId = "tenantId";
        }
        if (resolvedUserId == null || resolvedUserId.isBlank()) {
            resolvedUserId = "userId";
        }
        return new ChatScope(resolvedTenantId, resolvedUserId);
    }

    /**
     * 解析并校验租户和用户范围
     *
     * @param chatId   会话 ID
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return 已校验聊天范围
     */
    public ChatScope required(String chatId, String tenantId, String userId) {
        ChatScope scope = resolve(chatId, tenantId, userId);
        if (scope.tenantId() == null || scope.tenantId().isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (scope.userId() == null || scope.userId().isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        return scope;
    }
}
