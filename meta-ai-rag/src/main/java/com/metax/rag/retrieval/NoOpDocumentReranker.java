package com.metax.rag.retrieval;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.lang.NonNull;

import java.util.List;

/**
 * NoOpDocumentReranker .
 *
 * <p>
 * rerank 空实现，直接返回原始召回文档列表
 *
 * <p>
 * 该类只用于占位和固定扩展点
 * 它不改变排序、不计算 rerankScore，也不代表项目已经启用真实 rerank 能力
 * 后续接入真实 rerank 模型时，应替换为新的 DocumentReranker 实现
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/1
 */
public class NoOpDocumentReranker implements DocumentReranker {

    /**
     * 返回原始召回结果
     *
     * @param query     当前检索 query
     * @param documents 向量库召回的候选文档
     * @return 原始文档列表
     */
    @Override
    @NonNull
    public List<Document> rerank(@NonNull Query query, @NonNull List<Document> documents) {
        return documents;
    }
}
