package com.metax.chat.file.event;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * MetaChatFileIndexingEventListenerTest .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/10
 */
class MetaChatFileIndexingEventListenerTest {

    /**
     * 监听器收到事务事件后应只转发给索引执行器
     */
    @Test
    void shouldForwardEventToIndexingExecutor() {
        MetaChatFileIndexingExecutor executor = mock(MetaChatFileIndexingExecutor.class);
        MetaChatFileIndexingEventListener listener = new MetaChatFileIndexingEventListener(executor);
        MetaChatFileIndexingEvent event = new MetaChatFileIndexingEvent(this, "f1");

        listener.handleMetaChatFileIndexingEvent(event);

        verify(executor).index("f1");
    }

    /**
     * 监听器方法不应声明 @Async，异步职责由执行器承担
     */
    @Test
    void listenerShouldNotBeAsync() throws NoSuchMethodException {
        Method method = MetaChatFileIndexingEventListener.class
                .getMethod("handleMetaChatFileIndexingEvent", MetaChatFileIndexingEvent.class);

        assertThat(method.getAnnotation(org.springframework.scheduling.annotation.Async.class)).isNull();
    }
}
