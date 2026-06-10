package com.metax.chat.support;

import org.springframework.stereotype.Component;

/**
 * ChatScopeResolver .
 *
 * <p>
 * 统一解析 tenantId 和 userId 的兜底关系
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
     * chatId 只是业务会话 ID，不能再用于反向解析 tenantId 或 userId
     * 会话隔离边界必须由独立的 tenantId、userId 和 chatId 共同表达
     *
     * @param chatId   会话 ID
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return 聊天范围
     */
    public ChatScope resolve(String chatId, String tenantId, String userId) {
        String resolvedTenantId = tenantId;
        String resolvedUserId = userId;
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
