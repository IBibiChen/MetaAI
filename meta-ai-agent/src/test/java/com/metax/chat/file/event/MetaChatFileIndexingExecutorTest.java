package com.metax.chat.file.event;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MetaChatFileIndexingExecutorTest .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/10
 */
class MetaChatFileIndexingExecutorTest {

    /**
     * 索引执行方法应绑定统一异步线程池
     */
    @Test
    void indexShouldUseTaskExecutor() throws NoSuchMethodException {
        Method method = MetaChatFileIndexingExecutor.class.getMethod("index", String.class);
        Async async = method.getAnnotation(Async.class);

        assertThat(async).isNotNull();
        assertThat(async.value()).isEqualTo("taskExecutor");
    }
}
