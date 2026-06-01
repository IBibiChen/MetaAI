package com.metax.rag.retrieval;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

import java.util.List;

/**
 * DocumentReranker .
 *
 * <p>
 * 项目内部 rerank 抽象，用于在 VectorStore 召回之后重新评估文档相关性
 *
 * <p>
 * 输入 documents 是向量库已经按相似度召回的候选 chunk
 * 输出 documents 应保持为后续 DocumentPostProcessor 可以继续处理的 Document 列表
 * 真实 rerank 实现可以接 cross-encoder、DashScope rerank、Jina rerank 或本地模型
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/1
 */
public interface DocumentReranker {

    /**
     * 对召回文档重新排序
     *
     * @param query     当前检索 query
     * @param documents 向量库召回的候选文档
     * @return rerank 后的文档列表
     */
    List<Document> rerank(Query query, List<Document> documents);
}
