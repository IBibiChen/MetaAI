package com.metax.rag.retrieval;

import java.util.List;

/**
 * RetrievalSearchResponse .
 *
 * <p>
 * RAG 直接检索调试响应，只返回向量库召回结果，不调用 ChatModel
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
public record RetrievalSearchResponse(
        /**
         * 本次检索 query
         */
        String query,
        /**
         * 本次检索过滤表达式
         */
        String filter,
        /**
         * 本次检索 topK
         */
        int topK,
        /**
         * 本次检索相似度阈值
         */
        double similarityThreshold,
        /**
         * 命中数量
         */
        int hitCount,
        /**
         * 命中 chunk 列表
         */
        List<RetrievalReference> hits
) {
}
