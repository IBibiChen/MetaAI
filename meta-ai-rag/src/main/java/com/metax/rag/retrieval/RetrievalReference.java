package com.metax.rag.retrieval;

import java.util.Map;

/**
 * RetrievalReference .
 *
 * <p>
 * RAG 回答引用来源，来自 RetrievalAugmentationAdvisor 写入的命中文档上下文
 *
 * <p>
 * 字段说明：reference 不是模型回答的一部分，而是检索阶段命中的原始 chunk
 * text 是 chunk 文本
 * score 是向量库返回的相似度分数
 * metadata 用于展示来源、定位 documentId / chunkId，并支持前端跳转原文
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
public record RetrievalReference(
        /**
         * 命中 chunk 文本
         */
        String text,
        /**
         * 向量库返回的相似度分数
         */
        Double score,
        /**
         * chunk metadata，用于展示来源和定位原始文档
         */
        Map<String, Object> metadata
) {
}
