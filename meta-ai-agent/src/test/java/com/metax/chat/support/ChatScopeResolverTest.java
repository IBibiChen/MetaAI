package com.metax.chat.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ChatScopeResolverTest .
 *
 * <p>
 * 聊天范围解析单元测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/10
 */
class ChatScopeResolverTest {

    private final ChatScopeResolver resolver = new ChatScopeResolver();

    /**
     * 显式传入的 tenantId 和 userId 优先级高于 chatId 兜底结构
     */
    @Test
    void resolveShouldPreferExplicitScope() {
        ChatScope scope = resolver.resolve("t1-u1-s1", "tenant-a", "user-a");

        assertThat(scope.tenantId()).isEqualTo("tenant-a");
        assertThat(scope.userId()).isEqualTo("user-a");
    }

    /**
     * tenantId 和 userId 缺失时不能再从 chatId 反向解析
     */
    @Test
    void resolveShouldNotParseScopeFromChatId() {
        ChatScope scope = resolver.resolve("t1-u1-s1", null, null);

        assertThat(scope.tenantId()).isEqualTo("tenantId");
        assertThat(scope.userId()).isEqualTo("userId");
    }

    /**
     * 空 chatId 应使用不含冒号的默认会话 ID
     */
    @Test
    void resolveChatIdShouldUseHyphenDefault() {
        assertThat(resolver.resolveChatId(null)).isEqualTo("tenantId-userId-sessionId");
    }
}
