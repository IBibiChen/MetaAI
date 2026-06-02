package com.metax.rag.retrieval;

import com.metax.rag.config.RagProperties;
import com.metax.rag.model.MetadataKeys;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DefaultDocumentPostProcessor .
 *
 * <p>
 * 默认检索后处理器，负责去重、限制上下文文档数和限制上下文总字符数
 *
 * <p>
 * Spring AI 的 DocumentPostProcessor 位于检索之后、prompt 增强之前
 * 这里处理的是已经从 VectorStore 召回的 Document 列表
 * 处理结果会进入 ContextualQueryAugmenter，最终成为模型可见的上下文
 *
 * <p>
 * 去重规则
 * 优先使用 chunkId，因为 chunkId 是 documentId + chunkIndex 构造，能稳定定位同一个片段
 * 如果 chunkId 缺失，则使用 contentHash 兜底，避免重复内容多次进入上下文
 * 如果两个字段都缺失，则保留该 Document，避免误删没有统一 metadata 的临时测试数据
 *
 * <p>
 * 上下文限制规则
 * maxContextDocuments 控制最多进入 prompt 的 chunk 数
 * maxContextChars 控制所有 chunk 文本的总字符数
 * 这两个限制用于减少弱相关内容、控制 token 成本，并降低 lost-in-the-middle 风险
 *
 * <p>
 * 当前实现不强依赖外部 rerank 服务
 * rerank 开关只作为后续接入 cross-encoder 或第三方 rerank 模型的扩展点
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/1
 */
public class DefaultDocumentPostProcessor implements DocumentPostProcessor {

    private final RagProperties.PostProcessor properties;

    private final DocumentReranker documentReranker;

    public DefaultDocumentPostProcessor(RagProperties.PostProcessor properties) {
        this(properties, new NoOpDocumentReranker());
    }

    public DefaultDocumentPostProcessor(RagProperties.PostProcessor properties, DocumentReranker documentReranker) {
        this.properties = properties;
        this.documentReranker = documentReranker;
    }

    /**
     * 执行检索后文档治理
     *
     * <p>
     * 当前顺序固定为 rerank 预留 -> 去重 -> 上下文数量和长度限制
     * NoOpDocumentReranker 不会改变文档顺序，只是为后续真实 rerank 固定扩展点
     *
     * @param query     当前检索 query
     * @param documents 向量库召回的候选文档
     * @return 最终进入 ContextualQueryAugmenter 的文档列表
     */
    @Override
    @NonNull
    public List<Document> process(@NonNull Query query, @NonNull List<Document> documents) {
        // 阶段 1：rerank 预留，真实 rerank 接入后先调整候选文档顺序
        // rerank 放在最前面，真实 rerank 接入后应先调整候选文档顺序，再进入去重和截断
        List<Document> reranked = properties.isRerankEnabled() ? documentReranker.rerank(query, documents) : documents;

        // 阶段 2：按 chunkId 或 contentHash 去重，避免重复 chunk 占用上下文名额
        // 去重在截断前执行，避免重复 chunk 占用有限的上下文名额
        List<Document> candidates = properties.isDeduplicateEnabled() ? deduplicate(reranked) : reranked;

        // 阶段 3：按文档数和总字符数截断，控制最终进入 prompt 的上下文规模
        // 最后限制文档数量和文本长度，控制最终 prompt 的上下文规模
        List<Document> limited = limit(candidates);

        // 阶段 4：记录后处理数量变化，details 接口可用它判断后处理是否过严
        RetrievalTrace.Builder traceBuilder = RetrievalTraceContext.builder(query);
        if (traceBuilder != null) {
            // 这里记录的是后处理视角的数量变化，用于判断文档治理是否过严
            traceBuilder.retrievedCount(documents.size())
                    .usedCount(limited.size());
        }
        return limited;
    }

    /**
     * 按稳定 chunk 标识去重
     *
     * @param documents 候选文档
     * @return 去重后的文档
     */
    private List<Document> deduplicate(List<Document> documents) {
        Set<String> seen = new HashSet<>();
        List<Document> result = new ArrayList<>();
        for (Document document : documents) {
            String key = deduplicateKey(document);
            // 缺少统一 metadata 的临时文档不参与去重，避免误删调试数据
            // 有 key 时使用 seen.add 的返回值判断是否第一次出现，保持原始召回顺序
            if (!StringUtils.hasText(key) || seen.add(key)) {
                result.add(document);
            }
        }
        return result;
    }

    /**
     * 解析文档去重 key
     *
     * @param document 候选文档
     * @return chunkId 或 contentHash
     */
    private String deduplicateKey(Document document) {
        // chunkId 优先级最高，同一个文档的不同 chunk 不会被误判成重复
        Object chunkId = document.getMetadata().get(MetadataKeys.CHUNK_ID);
        if (chunkId != null) {
            return chunkId.toString();
        }
        // contentHash 是兜底方案，适合同一内容被多次写入向量库后的重复治理
        Object contentHash = document.getMetadata().get(MetadataKeys.CONTENT_HASH);
        return contentHash == null ? null : contentHash.toString();
    }

    /**
     * 限制最终上下文规模
     *
     * @param documents 候选文档
     * @return 截断后的文档列表
     */
    private List<Document> limit(List<Document> documents) {
        List<Document> result = new ArrayList<>();
        int totalChars = 0;
        for (Document document : documents) {
            if (result.size() >= properties.getMaxContextDocuments()) {
                // 文档数量达到上限后直接停止，保留前面相关度更高的候选文档
                break;
            }
            String text = document.getText();
            int length = text == null ? 0 : text.length();
            // 超出总字符上限时停止追加，避免后续 prompt 上下文过长
            if (totalChars + length > properties.getMaxContextChars()) {
                break;
            }
            result.add(document);
            totalChars += length;
        }
        return result;
    }
}
