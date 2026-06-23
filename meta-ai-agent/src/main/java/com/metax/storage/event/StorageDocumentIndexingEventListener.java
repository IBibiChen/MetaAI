package com.metax.storage.event;

import com.metax.rag.indexing.DocumentIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * StorageDocumentIndexingEventListener .
 *
 * <p>
 * 对象存储文档索引事件监听器，在 storage 事务提交后启动 RAG ETL
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageDocumentIndexingEventListener {

    private final DocumentIndexingService documentIndexingService;

    /**
     * 处理对象存储文档索引事件
     *
     * <p>
     * AFTER_COMMIT 确保 latestIndexingRunId 已经提交，observer 回写索引终态时可以命中文档
     *
     * @param event 对象存储文档索引事件
     */
    @TransactionalEventListener(value = StorageDocumentIndexingEvent.class,
            fallbackExecution = false,
            phase = TransactionPhase.AFTER_COMMIT)
    public void handleStorageDocumentIndexingEvent(StorageDocumentIndexingEvent event) {
        log.info("对象存储文档索引事务已提交，提交 RAG 后台启动：runId = {}，documentId = {}",
                event.submission().run().runId(), event.submission().run().documentId());
        documentIndexingService.start(event.submission());
    }
}
