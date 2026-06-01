package com.metax.rag.retrieval;

import org.springframework.ai.rag.Query;

/**
 * RetrievalTraceContext .
 *
 * <p>
 * RAG trace 上下文工具，负责从 Spring AI Query context 中读写 RetrievalTrace.Builder
 *
 * <p>
 * RetrievalAugmentationAdvisor 内部会把 ChatClient advisor context 复制到 Query context
 * 因此 Controller 可以把 RetrievalTrace.Builder 放入 advisor context
 * QueryTransformer 和 DocumentPostProcessor 再从 Query context 中取出同一个 builder 追加信息
 *
 * <p>
 * 这种方式不修改 Spring AI 官方类，也不把 trace 放进 prompt
 * trace 只在 details 接口中通过 ChatClientResponse context 取回
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/1
 */
final class RetrievalTraceContext {

    private RetrievalTraceContext() {
    }

    static RetrievalTrace.Builder builder(Query query) {
        Object value = query.context().get(RetrievalTrace.CONTEXT_KEY);
        if (value instanceof RetrievalTrace.Builder builder) {
            return builder;
        }
        return null;
    }
}
