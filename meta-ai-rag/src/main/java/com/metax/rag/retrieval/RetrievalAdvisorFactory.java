package com.metax.rag.retrieval;

import com.metax.rag.config.RagProperties;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Component;

/**
 * RetrievalAdvisorFactory .
 *
 * <p>
 * RAG Advisor 工厂，统一构造 RetrievalAugmentationAdvisor
 * 检索默认参数来自配置文件，请求侧可通过 advisor context 覆盖 filterExpression
 *
 * <p>
 * 链路说明：RetrievalAugmentationAdvisor 是 Spring AI 的模块化 RAG 入口
 * 它把一次用户问题拆成检索前、检索、文档合并、文档后处理、提示词增强几个阶段
 * 当前项目第一版只启用 VectorStoreDocumentRetriever 和 ContextualQueryAugmenter
 *
 * <p>
 * VectorStoreDocumentRetriever 的职责
 * 使用用户问题生成 query embedding，并到 VectorStore 做 similaritySearch
 * topK 控制最多返回多少个 chunk
 * similarityThreshold 控制最低相似度，低于阈值的结果会被过滤
 * filterExpression 控制 metadata 过滤，例如租户、知识库、文档类型
 *
 * <p>
 * ChatClient 使用示例
 * <pre>{@code
 * chatClient.prompt()
 *     .advisors(spec -> spec.advisors(retrievalAdvisorFactory.create(vectorStore, 5, 0.5, filter)))
 *     .user("Spring AI 的 ETL 是什么")
 *     .call()
 *     .content()
 * }</pre>
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
@Component
public class RetrievalAdvisorFactory {

    private final RagProperties properties;

    public RetrievalAdvisorFactory(RagProperties properties) {
        this.properties = properties;
    }

    /**
     * 构造模块化 RAG Advisor
     *
     * <p>
     * 使用配置文件中的默认 topK 和 similarityThreshold
     * 适合普通 RAG 接口和没有特殊检索参数的业务调用
     *
     * @param vectorStore 向量库
     * @return Advisor
     */
    public Advisor create(VectorStore vectorStore) {
        return create(vectorStore, properties.getRetrieval().getTopK(),
                properties.getRetrieval().getSimilarityThreshold(), null);
    }

    /**
     * 构造模块化 RAG Advisor
     *
     * <p>
     * 该方法适合 details 接口或调试场景
     * topK 和 similarityThreshold 允许按请求覆盖，便于比较不同召回参数的效果
     *
     * @param vectorStore         向量库
     * @param topK                检索数量
     * @param similarityThreshold 相似度阈值
     * @param filterExpression    默认过滤表达式
     * @return Advisor
     */
    public Advisor create(VectorStore vectorStore,
                          Integer topK,
                          Double similarityThreshold,
                          Filter.Expression filterExpression) {
        int resolvedTopK = topK == null ? properties.getRetrieval().getTopK() : topK;
        double resolvedSimilarityThreshold = similarityThreshold == null
                ? properties.getRetrieval().getSimilarityThreshold() : similarityThreshold;
        // retriever 是真正访问 VectorStore 的组件，Advisor 只是把它接入 ChatClient 调用链
        VectorStoreDocumentRetriever.Builder retrieverBuilder = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(resolvedTopK)
                .similarityThreshold(resolvedSimilarityThreshold);
        if (filterExpression != null) {
            retrieverBuilder.filterExpression(filterExpression);
        }
        // allowEmptyContext=false 表示没有召回上下文时不让模型自由发挥，避免 RAG 场景编造答案
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retrieverBuilder.build())
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(false)
                        .build())
                .build();
    }
}
