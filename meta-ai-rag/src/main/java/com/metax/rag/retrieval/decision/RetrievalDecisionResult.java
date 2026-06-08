package com.metax.rag.retrieval.decision;

/**
 * RetrievalDecisionResult .
 *
 * <p>
 * 检索决策结果，reason 用于日志和排查当前轮为什么检索或跳过检索
 *
 * @param decision 检索决策
 * @param reason   决策原因
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
public record RetrievalDecisionResult(
        /**
         * 检索决策
         */
        RetrievalDecision decision,
        /**
         * 决策原因
         */
        String reason
) {

    public static RetrievalDecisionResult retrieve(String reason) {
        return new RetrievalDecisionResult(RetrievalDecision.RETRIEVE, reason);
    }

    public static RetrievalDecisionResult skip(String reason) {
        return new RetrievalDecisionResult(RetrievalDecision.SKIP, reason);
    }
}
