package com.metax.storage;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.metax.rag.indexing.DocumentIndexingRun;
import com.metax.rag.indexing.DocumentIndexingRunObserver;
import com.metax.rag.indexing.DocumentIndexingStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * StorageDocumentIndexingRunObserver .
 *
 * <p>
 * 对象存储文档索引执行观察者，负责把 RAG 异步执行结果回写到文档元数据表
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
@Component
public class StorageDocumentIndexingRunObserver implements DocumentIndexingRunObserver {

    private final StorageDocumentMapper storageDocumentMapper;

    public StorageDocumentIndexingRunObserver(StorageDocumentMapper storageDocumentMapper) {
        this.storageDocumentMapper = storageDocumentMapper;
    }

    /**
     * 文档索引执行状态变化
     *
     * @param run 文档索引执行
     */
    @Override
    public void onRunChanged(DocumentIndexingRun run) {
        StorageDocumentIndexStatus indexStatus = switch (run.status()) {
            case PENDING, RUNNING -> StorageDocumentIndexStatus.INDEXING;
            case SUCCEEDED -> StorageDocumentIndexStatus.INDEXED;
            case FAILED -> StorageDocumentIndexStatus.INDEX_FAILED;
        };
        storageDocumentMapper.update(null, new LambdaUpdateWrapper<StorageDocumentDO>()
                .eq(StorageDocumentDO::getTenantId, run.tenantId())
                .eq(StorageDocumentDO::getKbId, run.kbId())
                .eq(StorageDocumentDO::getDocumentId, run.documentId())
                .eq(StorageDocumentDO::getLatestIndexingRunId, run.runId())
                .eq(StorageDocumentDO::getDeleted, Boolean.FALSE)
                .set(StorageDocumentDO::getIndexStatus, indexStatus.name())
                .set(run.status() == DocumentIndexingStatus.SUCCEEDED, StorageDocumentDO::getChunkCount,
                        run.chunkCount())
                .set(StorageDocumentDO::getUpdatedAt, Instant.now()));
    }
}
