package com.metax.rag.retrieval.search;

import com.metax.rag.config.MetaRetrievalProperties;
import com.metax.rag.retrieval.filter.RetrievalFilterExpressionFactory;
import com.metax.rag.retrieval.model.RetrievalChunkReference;
import com.metax.rag.retrieval.model.RetrievalOptions;
import com.metax.rag.retrieval.model.RetrievalSearchResponse;
import cn.hutool.core.date.TimeInterval;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * RetrievalSearchService .
 *
 * <p>
 * RAG 直接检索服务，用于绕过 ChatClient 排查 VectorStore 召回质量
 * 该服务不调用 ChatModel，不构造 prompt，也不经过 RetrievalAugmentationAdvisor
 * 适合在模型回答不符合预期时，单独确认 query、filter、topK 和 similarityThreshold 是否能召回正确 chunk
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
@Slf4j
@Service
public class RetrievalSearchService {

    private final MetaRetrievalProperties properties;

    private final RetrievalFilterExpressionFactory filterExpressionFactory;

    public RetrievalSearchService(MetaRetrievalProperties properties,
                                  RetrievalFilterExpressionFactory filterExpressionFactory) {
        this.properties = properties;
        this.filterExpressionFactory = filterExpressionFactory;
    }

    /**
     * 直接检索向量库
     *
     * @param vectorStore 向量库
     * @param options     检索参数
     * @return 检索响应
     */
    public RetrievalSearchResponse search(VectorStore vectorStore, RetrievalOptions options) {
        TimeInterval timer = new TimeInterval();
        // 请求参数优先，未传时回落到 RAG 全局检索配置
        int resolvedTopK = resolvedTopK(options);
        double resolvedSimilarityThreshold = resolvedSimilarityThreshold(options);
        // 直接检索也必须使用结构化过滤表达式，不能让原始 filterExpression 绕过权限边界
        Filter.Expression filterExpression = filterExpressionFactory.create(options);
        // SearchRequest 会直接进入 VectorStore.similaritySearch，不经过 ChatClient 和 ChatModel
        SearchRequest.Builder requestBuilder = SearchRequest.builder()
                .query(options.getQuery())
                .topK(resolvedTopK)
                .similarityThreshold(resolvedSimilarityThreshold);
        if (filterExpression != null) {
            requestBuilder.filterExpression(filterExpression);
        }
        List<Document> documents = vectorStore.similaritySearch(requestBuilder.build());
        // 直接返回命中 chunk 和 metadata，方便定位 documentId、chunkId、source、documentName 等来源信息
        List<RetrievalChunkReference> hits = documents.stream()
                .map(document -> new RetrievalChunkReference(document.getText(), document.getScore(),
                        document.getMetadata()))
                .toList();
        log.info("RAG 检索完成：tenantId = {}，kbId = {}，topK = {}，similarityThreshold = {}，hitCount = {}，durationMs = {}",
                options.getTenantId(), options.getKbId(), resolvedTopK, resolvedSimilarityThreshold, hits.size(),
                timer.intervalMs());
        return new RetrievalSearchResponse(options.getQuery(), filter(options, filterExpression), resolvedTopK,
                resolvedSimilarityThreshold, hits.size(), hits);
    }

    /**
     * 解析本次检索 topK
     *
     * @param options 检索参数
     * @return 本次实际使用的 topK
     */
    int resolvedTopK(RetrievalOptions options) {
        return options.getTopK() == null ? properties.getSearch().getTopK() : options.getTopK();
    }

    /**
     * 解析本次检索相似度阈值
     *
     * @param options 检索参数
     * @return 本次实际使用的相似度阈值
     */
    double resolvedSimilarityThreshold(RetrievalOptions options) {
        return options.getSimilarityThreshold() == null
                ? properties.getSearch().getSimilarityThreshold() : options.getSimilarityThreshold();
    }

    /**
     * 解析响应展示用过滤表达式
     *
     * <p>
     * 原始 filterExpression 只用于调试响应展示，不代表实际检索使用它
     *
     * @param options          检索参数
     * @param filterExpression 实际检索使用的结构化过滤表达式
     * @return 响应展示用过滤表达式
     */
    private String filter(RetrievalOptions options, Filter.Expression filterExpression) {
        if (StringUtils.hasText(options.getFilterExpression())) {
            return options.getFilterExpression();
        }
        return String.valueOf(filterExpression);
    }
}
