package com.metax.rag.indexing;

/**
 * DocumentIndexingSubmission .
 *
 * <p>
 * RAG 文档索引提交结果，保存可观察 run 和启动索引所需上下文
 *
 * @param run     文档索引执行
 * @param context 文档索引上下文
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/23
 */
public record DocumentIndexingSubmission(
        DocumentIndexingRun run,
        DocumentIndexingContext context
) {
}
