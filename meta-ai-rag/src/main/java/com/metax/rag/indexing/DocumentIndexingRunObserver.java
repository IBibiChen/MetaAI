package com.metax.rag.indexing;

/**
 * DocumentIndexingRunObserver .
 *
 * <p>
 * 文档索引执行状态观察者，用于把 RAG 异步执行结果同步给业务模块
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
public interface DocumentIndexingRunObserver {

    /**
     * 文档索引执行状态变化
     *
     * @param run 文档索引执行
     */
    void onRunChanged(DocumentIndexingRun run);
}
