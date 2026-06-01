package com.metax.rag.retrieval;

import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.lang.NonNull;

/**
 * TracingQueryTransformer .
 *
 * <p>
 * QueryTransformer 装饰器，委托 Spring AI 官方 transformer 执行，并记录转换结果与耗时
 *
 * <p>
 * 装饰器只负责观测，不改变 CompressionQueryTransformer 或 RewriteQueryTransformer 的行为
 * 原始 query 和转换后 query 都通过 RetrievalTrace 返回给 details 接口
 * 如果官方 transformer 返回空文本，Spring AI transformer 自身会回退到原 query
 *
 * <p>
 * 记录 queryTransform 耗时可以帮助判断一次 RAG 慢在检索前 query 转换，还是慢在向量库或模型生成
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/1
 */
public class TracingQueryTransformer implements QueryTransformer {

    private final QueryTransformer delegate;

    private final String mode;

    public TracingQueryTransformer(QueryTransformer delegate, String mode) {
        this.delegate = delegate;
        this.mode = mode;
    }

    /**
     * 执行 query 转换并记录 trace
     *
     * @param query 原始 query
     * @return 转换后的 query
     */
    @Override
    @NonNull
    public Query transform(@NonNull Query query) {
        long start = System.nanoTime();
        // 真正的 query 转换仍由 Spring AI 官方 transformer 完成
        Query transformed = delegate.transform(query);
        RetrievalTrace.Builder traceBuilder = RetrievalTraceContext.builder(query);
        if (traceBuilder != null) {
            // trace 只记录转换结果和耗时，不参与 prompt 构造
            // details 接口可以用 transformedQuery 判断模型是否把用户问题改偏
            traceBuilder.queryTransformerMode(mode)
                    .transformedQuery(transformed.text())
                    .timing("queryTransform", elapsedMillis(start));
        }
        return transformed;
    }

    private long elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
