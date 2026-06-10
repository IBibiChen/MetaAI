package com.metax.chat.file.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * MetaChatFileIndexingEventListener .
 *
 * <p>
 * 会话文件索引事件监听器，在上传事务提交后触发异步索引执行器
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/10
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetaChatFileIndexingEventListener {

    private final MetaChatFileIndexingExecutor indexingExecutor;

    /**
     * 处理会话文件索引事件
     *
     * <p>
     * AFTER_COMMIT 确保文件元数据已经提交，异步执行器可以按 fileId 查询到文件记录
     * 监听器只做事件转发，真正耗时的 OCR、chunk 和向量写入由执行器异步完成
     *
     * @param event 会话文件索引事件
     */
    @TransactionalEventListener(value = MetaChatFileIndexingEvent.class,
            fallbackExecution = false,
            phase = TransactionPhase.AFTER_COMMIT)
    public void handleMetaChatFileIndexingEvent(MetaChatFileIndexingEvent event) {
        log.info("监听到会话文件上传事务提交，准备触发索引任务：fileId = {}", event.getFileId());
        indexingExecutor.index(event.getFileId());
    }
}
