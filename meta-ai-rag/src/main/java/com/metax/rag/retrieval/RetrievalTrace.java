package com.metax.rag.retrieval;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RetrievalTrace .
 *
 * <p>
 * RAG 检索链路 trace，记录 query 转换、检索参数、召回数量和阶段耗时
 * 只用于 details 调试响应，不参与模型 prompt 构造
 *
 * <p>
 * trace 的用途不是给模型看，而是给开发者和运营排查检索质量
 * 当回答不准确时，可以先看 transformedQuery 是否偏离用户原意
 * 再看 filter 是否包含 tenantId 和 knowledgeBaseId
 * 最后看 retrievedCount 与 usedCount 判断是召回不足、后处理过严还是上下文过长
 *
 * <p>
 * Builder 是跨 RAG 组件共享的可变 trace 收集器，不是普通 DTO 构造器
 * Controller 先把 Builder 放入 advisor context
 * QueryTransformer、DocumentPostProcessor 在执行过程中分阶段写入数据
 * ResponseAssembler 最后调用 build 生成不可变 RetrievalTrace 快照
 *
 * <p>
 * details 响应示例
 * <pre>{@code
 * {
 *   "query": "上面第二点是什么意思",
 *   "transformedQuery": "Spring AI RAG 文档中第二点的含义",
 *   "queryTransformerMode": "compression",
 *   "filter": "tenantId == 't1' && knowledgeBaseId == 'kb1'",
 *   "topK": 5,
 *   "similarityThreshold": 0.5,
 *   "retrievedCount": 5,
 *   "usedCount": 3,
 *   "timings": {
 *     "queryTransform": 120,
 *     "postProcess": 2
 *   }
 * }</pre>
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/1
 */
public record RetrievalTrace(
        /**
         * 原始用户 query
         */
        String query,
        /**
         * query 转换后的文本，未启用转换时为空
         */
        String transformedQuery,
        /**
         * query 转换模式
         */
        String queryTransformerMode,
        /**
         * 检索过滤表达式摘要
         */
        String filter,
        /**
         * 本次检索 topK
         */
        Integer topK,
        /**
         * 本次检索相似度阈值
         */
        Double similarityThreshold,
        /**
         * 向量库原始召回数量
         */
        int retrievedCount,
        /**
         * 后处理后进入上下文的文档数量
         */
        int usedCount,
        /**
         * 阶段耗时，单位毫秒
         */
        Map<String, Long> timings
) {

    public static final String CONTEXT_KEY = "metax_rag_retrieval_trace";

    /**
     * 创建 trace 收集器
     *
     * <p>
     * 返回的 Builder 会放入 advisor context，在一次 RAG 调用中被多个组件陆续写入
     *
     * @param query 原始用户 query
     * @return trace 收集器
     */
    public static Builder builder(String query) {
        return new Builder(query);
    }

    /**
     * RAG 检索链路可变 trace 收集器
     *
     * <p>
     * 该类手写而不是使用 Lombok @Builder，是因为 trace 数据来自多个阶段
     * QueryTransformer 负责写入 query 转换信息
     * DocumentPostProcessor 负责写入召回数量、使用数量、过滤表达式和后处理耗时
     * build 方法负责把当前累计状态冻结成 RetrievalTrace 响应快照
     */
    public static final class Builder {

        private final String query;

        private String transformedQuery;

        private String queryTransformerMode = "none";

        private String filter;

        private Integer topK;

        private Double similarityThreshold;

        private int retrievedCount;

        private int usedCount;

        private final Map<String, Long> timings = new LinkedHashMap<>();

        private Builder(String query) {
            this.query = query;
        }

        /**
         * 记录转换后的 query
         *
         * @param transformedQuery 转换后的 query
         * @return Builder
         */
        public Builder transformedQuery(String transformedQuery) {
            this.transformedQuery = transformedQuery;
            return this;
        }

        /**
         * 记录 query transformer 模式
         *
         * @param queryTransformerMode query transformer 模式
         * @return Builder
         */
        public Builder queryTransformerMode(String queryTransformerMode) {
            this.queryTransformerMode = queryTransformerMode;
            return this;
        }

        /**
         * 记录过滤表达式
         *
         * @param filter 过滤表达式
         * @return Builder
         */
        public Builder filter(String filter) {
            this.filter = filter;
            return this;
        }

        /**
         * 记录 topK
         *
         * @param topK 检索数量
         * @return Builder
         */
        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        /**
         * 记录最终生效的 topK
         *
         * @param topK 检索数量
         * @return Builder
         */
        public Builder resolvedTopK(int topK) {
            this.topK = topK;
            return this;
        }

        /**
         * 记录相似度阈值
         *
         * @param similarityThreshold 相似度阈值
         * @return Builder
         */
        public Builder similarityThreshold(Double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
            return this;
        }

        /**
         * 记录最终生效的相似度阈值
         *
         * @param similarityThreshold 相似度阈值
         * @return Builder
         */
        public Builder resolvedSimilarityThreshold(double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
            return this;
        }

        /**
         * 记录原始召回数量
         *
         * @param retrievedCount 原始召回数量
         * @return Builder
         */
        public Builder retrievedCount(int retrievedCount) {
            this.retrievedCount = retrievedCount;
            return this;
        }

        /**
         * 记录最终使用数量
         *
         * @param usedCount 最终使用数量
         * @return Builder
         */
        public Builder usedCount(int usedCount) {
            this.usedCount = usedCount;
            return this;
        }

        /**
         * 记录阶段耗时
         *
         * <p>
         * timings 使用 LinkedHashMap 保留收集过程的写入顺序
         *
         * @param stage  阶段名称
         * @param millis 耗时毫秒
         * @return Builder
         */
        public Builder timing(String stage, long millis) {
            this.timings.put(stage, millis);
            return this;
        }

        /**
         * 构建 trace 快照
         *
         * <p>
         * build 时复制 timings，避免响应对象继续受到后续写入影响
         *
         * @return RetrievalTrace
         */
        public RetrievalTrace build() {
            return new RetrievalTrace(query, transformedQuery, queryTransformerMode, filter, topK,
                    similarityThreshold, retrievedCount, usedCount, Map.copyOf(timings));
        }
    }
}
