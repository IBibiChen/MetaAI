package com.metax.chat.history;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MetaChatHistoryServiceTest .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/2
 */
class MetaChatHistoryServiceTest {

    @SuppressWarnings("unchecked")
    private static final Class<Page<MetaChatHistoryDO>> PAGE_CLASS = (Class<Page<MetaChatHistoryDO>>) (Class<?>) Page.class;

    @SuppressWarnings("unchecked")
    private static final Class<Wrapper<MetaChatHistoryDO>> WRAPPER_CLASS =
            (Class<Wrapper<MetaChatHistoryDO>>) (Class<?>) Wrapper.class;

    /**
     * 保存历史消息应走 MyBatis-Plus Mapper
     */
    @Test
    void shouldSaveHistoryMessageWithMapper() {
        MetaChatHistoryMapper mapper = mock(MetaChatHistoryMapper.class);
        MetaChatHistoryServiceImpl service = service(mapper);

        service.saveUserMessage("c1", MetaChatHistoryType.CHAT, "你好");

        verify(mapper).insert(any(MetaChatHistoryDO.class));
    }

    /**
     * 完整历史应使用 MyBatis-Plus Page 分页查询
     */
    @Test
    void shouldPageHistoryMessagesWithMyBatisPlusPage() {
        MetaChatHistoryMapper mapper = mock(MetaChatHistoryMapper.class);
        MetaChatHistoryServiceImpl service = service(mapper);
        Page<MetaChatHistoryDO> mapperPage = Page.of(2, 10);
        mapperPage.setRecords(java.util.List.of(entity("c1", MetaChatHistoryRole.USER)));
        mapperPage.setTotal(1);
        when(mapper.selectPage(any(PAGE_CLASS), any(WRAPPER_CLASS))).thenReturn(mapperPage);

        Page<MetaChatHistoryDO> page = service.pageByChatId("c1", 2L, 10L);

        assertThat(page.getCurrent()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(10);
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords())
                .extracting(MetaChatHistoryDO::getRole)
                .containsExactly(MetaChatHistoryRole.USER.value());
    }

    /**
     * 空分页参数应使用默认值
     */
    @Test
    void shouldUseDefaultPageParams() {
        MetaChatHistoryMapper mapper = mock(MetaChatHistoryMapper.class);
        MetaChatHistoryServiceImpl service = service(mapper);
        when(mapper.selectPage(any(PAGE_CLASS), any(WRAPPER_CLASS))).thenAnswer(invocation -> invocation.getArgument(0));

        Page<MetaChatHistoryDO> page = service.pageByChatId("c1", null, null);

        assertThat(page.getCurrent()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(20);
    }

    private MetaChatHistoryServiceImpl service(MetaChatHistoryMapper mapper) {
        MetaChatHistoryServiceImpl service = new MetaChatHistoryServiceImpl(new ObjectMapper());
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        return service;
    }

    private MetaChatHistoryDO entity(String chatId, MetaChatHistoryRole role) {
        return new MetaChatHistoryDO(1L, null, chatId, MetaChatHistoryType.CHAT.value(),
                role.value(), "content", null, Instant.now());
    }
}
