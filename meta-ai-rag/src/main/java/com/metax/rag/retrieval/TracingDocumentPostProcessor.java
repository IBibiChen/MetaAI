package com.metax.rag.retrieval;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;

/**
 * TracingDocumentPostProcessor .
 *
 * <p>
 * DocumentPostProcessor 装饰器，记录检索后处理耗时
 *
 * <p>
 * retrievedCount 表示 VectorStoreDocumentRetriever 原始召回的 Document 数量
 * usedCount 表示 DefaultDocumentPostProcessor 处理后最终进入 ContextualQueryAugmenter 的 Document 数量
 * 两者差异可以帮助判断去重、上下文数量限制或上下文长度限制是否过严
 *
 * <p>
 * filter、topK、similarityThreshold 也在这里写入 trace
 * 因为 DocumentPostProcessor 位于检索之后，此时这些参数已经是本次请求实际采用的最终值
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/1
 */
public class TracingDocumentPostProcessor implements DocumentPostProcessor {

    private final DocumentPostProcessor delegate;

    private final Integer topK;

    private final Double similarityThreshold;

    private final Filter.Expression filterExpression;

    public TracingDocumentPostProcessor(DocumentPostProcessor delegate,
                                        Integer topK,
                                        Double similarityThreshold,
                                        Filter.Expression filterExpression) {
        this.delegate = delegate;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
        this.filterExpression = filterExpression;
    }

    /**
     * 执行检索后处理并记录 trace
     *
     * @param query     当前检索 query
     * @param documents 向量库召回的候选文档
     * @return 后处理后的文档
     */
    @Override
    public List<Document> process(Query query, List<Document> documents) {
        long start = System.nanoTime();
        List<Document> processed = delegate.process(query, documents);
        RetrievalTrace.Builder traceBuilder = RetrievalTraceContext.builder(query);
        if (traceBuilder != null) {
            // postProcess 阶段能看到处理前后数量，适合记录 retrievedCount 和 usedCount
            traceBuilder.topK(topK)
                    .similarityThreshold(similarityThreshold)
                    .retrievedCount(documents.size())
                    .usedCount(processed.size())
                    .timing("postProcess", elapsedMillis(start));
            if (filterExpression != null) {
                traceBuilder.filter(filterExpression.toString());
            }
        }
        return processed;
    }

    private long elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
