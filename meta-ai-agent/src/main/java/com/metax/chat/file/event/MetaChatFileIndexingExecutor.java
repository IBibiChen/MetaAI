package com.metax.chat.file.event;

import com.metax.chat.file.MetaChatFileIndexingService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * MetaChatFileIndexingExecutor .
 *
 * <p>
 * 会话文件索引异步执行器，只负责把事务事件后的索引请求切换到统一异步线程池
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/10
 */
@Component
@RequiredArgsConstructor
public class MetaChatFileIndexingExecutor {

    private final MetaChatFileIndexingService indexingService;

    /**
     * 异步执行会话文件临时索引
     *
     * <p>
     * 该方法绑定统一 taskExecutor 线程池
     * 文件记录必须由事务事件监听器在 AFTER_COMMIT 后触发，确保后台线程能查询到文件元数据
     *
     * @param fileId 文件 ID
     */
    @Async("taskExecutor")
    public void index(String fileId) {
        indexingService.index(fileId);
    }
}
