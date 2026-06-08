package com.metax.rag.retrieval.decision;

/**
 * RetrievalDecision .
 *
 * <p>
 * 普通 RAG 对话当前轮是否执行知识库检索的决策结果
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
public enum RetrievalDecision {

    /**
     * 执行知识库检索
     */
    RETRIEVE,

    /**
     * 跳过知识库检索
     */
    SKIP
}
