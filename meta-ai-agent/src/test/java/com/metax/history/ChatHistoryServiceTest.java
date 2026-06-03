package com.metax.history;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatHistoryServiceTest .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/2
 */
class ChatHistoryServiceTest {

    @SuppressWarnings("unchecked")
    private static final Class<Page<ChatHistoryDO>> PAGE_CLASS = (Class<Page<ChatHistoryDO>>) (Class<?>) Page.class;

    @SuppressWarnings("unchecked")
    private static final Class<Wrapper<ChatHistoryDO>> WRAPPER_CLASS =
            (Class<Wrapper<ChatHistoryDO>>) (Class<?>) Wrapper.class;

    /**
     * 保存历史消息应走 MyBatis-Plus Mapper
     */
    @Test
    void shouldSaveHistoryMessageWithMapper() {
        ChatHistoryMapper mapper = mock(ChatHistoryMapper.class);
        ChatHistoryServiceImpl service = service(mapper);

        service.saveUserMessage("c1", ChatHistoryType.CHAT, "你好");

        verify(mapper).insert(any(ChatHistoryDO.class));
    }

    /**
     * 完整历史应使用 MyBatis-Plus Page 分页查询
     */
    @Test
    void shouldPageHistoryMessagesWithMyBatisPlusPage() {
        ChatHistoryMapper mapper = mock(ChatHistoryMapper.class);
        ChatHistoryServiceImpl service = service(mapper);
        Page<ChatHistoryDO> mapperPage = Page.of(2, 10);
        mapperPage.setRecords(java.util.List.of(entity("c1", ChatHistoryRole.USER)));
        mapperPage.setTotal(1);
        when(mapper.selectPage(any(PAGE_CLASS), any(WRAPPER_CLASS))).thenReturn(mapperPage);

        Page<ChatHistoryDO> page = service.pageByConversationId("c1", 2L, 10L);

        assertThat(page.getCurrent()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(10);
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords())
                .extracting(ChatHistoryDO::getRole)
                .containsExactly(ChatHistoryRole.USER.value());
    }

    /**
     * 空分页参数应使用默认值
     */
    @Test
    void shouldUseDefaultPageParams() {
        ChatHistoryMapper mapper = mock(ChatHistoryMapper.class);
        ChatHistoryServiceImpl service = service(mapper);
        when(mapper.selectPage(any(PAGE_CLASS), any(WRAPPER_CLASS))).thenAnswer(invocation -> invocation.getArgument(0));

        Page<ChatHistoryDO> page = service.pageByConversationId("c1", null, null);

        assertThat(page.getCurrent()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(20);
    }

    private ChatHistoryServiceImpl service(ChatHistoryMapper mapper) {
        ChatHistoryServiceImpl service = new ChatHistoryServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        return service;
    }

    private ChatHistoryDO entity(String conversationId, ChatHistoryRole role) {
        return new ChatHistoryDO(1L, conversationId, ChatHistoryType.CHAT.value(),
                role.value(), "content", Instant.now());
    }
}
