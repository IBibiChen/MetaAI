package com.metax.rag.retrieval.postprocess;

import com.metax.rag.config.MetaRetrievalProperties;
import com.metax.rag.model.MetadataKeys;
import com.metax.rag.retrieval.trace.RetrievalTrace;
import com.metax.rag.retrieval.trace.RetrievalTraceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
@Slf4j
public class DefaultDocumentPostProcessor implements DocumentPostProcessor {

    private final MetaRetrievalProperties.PostProcessor properties;

    private final DocumentReranker documentReranker;

    public DefaultDocumentPostProcessor(MetaRetrievalProperties.PostProcessor properties) {
        this(properties, new NoOpDocumentReranker());
    }

    public DefaultDocumentPostProcessor(MetaRetrievalProperties.PostProcessor properties, DocumentReranker documentReranker) {
        this.properties = properties;
        this.documentReranker = documentReranker;
    }

    /**
     * 执行检索后文档治理
     *
     * <p>
     * 当前顺序固定为 rerank 预留 -> 分数过滤 -> 去重 -> 文件聚合 -> 上下文数量和长度限制
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

        // 阶段 2：过滤低可信分 chunk，减少明显弱相关内容进入 prompt
        // 缺少 score 的 Document 保守放行，避免某些 VectorStore 实现不返回分数时上下文被清空
        List<Document> scoreFiltered = filterByScore(reranked);

        // 阶段 3：按 chunkId 或 contentHash 去重，避免重复 chunk 占用上下文名额
        // 去重在截断前执行，避免重复 chunk 占用有限的上下文名额
        List<Document> deduplicated = properties.isDeduplicateEnabled() ? deduplicate(scoreFiltered) : scoreFiltered;

        // 阶段 4：按 documentId 聚合并限制单文件 chunk 数，避免 references 被弱相关文件污染
        // 文件级最高分排序后再展开，保证更可信的文件优先进入有限上下文
        List<Document> candidates = groupByDocument(deduplicated);

        // 阶段 5：按文档数和总字符数截断，控制最终进入 prompt 的上下文规模
        // 最后限制文档数量和文本长度，控制最终 prompt 的上下文规模
        List<Document> limited = limit(candidates);

        // 阶段 6：记录后处理数量变化，details 接口可用它判断后处理是否过严
        RetrievalTrace.Builder traceBuilder = RetrievalTraceContext.builder(query);
        if (traceBuilder != null) {
            // 这里记录的是后处理视角的数量变化，用于判断文档治理是否过严
            traceBuilder.retrievedCount(documents.size())
                    .usedCount(limited.size());
        }
        log.debug("RAG 检索后处理完成：retrievedCount = {}，scoreFilteredCount = {}，deduplicatedCount = {}，"
                        + "groupedCount = {}，usedCount = {}，minContextScore = {}",
                documents.size(), scoreFiltered.size(), deduplicated.size(), candidates.size(), limited.size(),
                properties.getMinContextScore());
        return limited;
    }

    /**
     * 过滤低可信分候选 chunk
     *
     * <p>
     * Document.score 来自 VectorStore 相似度结果
     * 缺失 score 时放行，保证不同向量库实现之间的兼容性
     *
     * @param documents 候选文档
     * @return 通过上下文分数阈值的文档
     */
    private List<Document> filterByScore(List<Document> documents) {
        double minContextScore = properties.getMinContextScore();
        if (minContextScore <= 0) {
            return documents;
        }
        return documents.stream()
                .filter(document -> document.getScore() == null || document.getScore() >= minContextScore)
                .toList();
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
     * 按文件聚合候选 chunk
     *
     * <p>
     * 有 documentId 的知识库 chunk 按文件聚合，同文件只保留最高分靠前的有限 chunk
     * 缺少 documentId 的临时文档保持原始顺序，避免调试数据被错误合并
     *
     * @param documents 候选文档
     * @return 文件级排序后展开的 chunk 列表
     */
    private List<Document> groupByDocument(List<Document> documents) {
        // maxChunksPerDocument 至少为 1，避免配置错误导致每个文件都无法进入上下文
        int maxChunksPerDocument = Math.max(1, properties.getMaxChunksPerDocument());
        Map<String, List<Document>> groups = new LinkedHashMap<>();
        List<Document> withoutDocumentId = new ArrayList<>();
        for (Document document : documents) {
            // documentId 是文件级聚合边界，同一文件的多个 chunk 先聚到同一组
            String documentId = metadataValue(document, MetadataKeys.DOCUMENT_ID);
            if (!StringUtils.hasText(documentId)) {
                // 缺少 documentId 的候选可能来自临时测试或非标准 VectorStore 数据
                // 这类候选不做文件级合并，保留原始顺序并放到标准知识库候选之后
                withoutDocumentId.add(document);
                continue;
            }
            // LinkedHashMap 保留首次出现顺序，后续再按文件最高分排序
            groups.computeIfAbsent(documentId, ignored -> new ArrayList<>()).add(document);
        }

        // 每个文件先收敛为 DocumentGroup，再用文件最高分做跨文件排序
        // 这样可以避免多个弱相关文件各占一个上下文名额
        List<DocumentGroup> rankedGroups = groups.values().stream()
                .map(group -> documentGroup(group, maxChunksPerDocument))
                .sorted(Comparator.comparingDouble(DocumentGroup::bestScore).reversed())
                .toList();

        // 排序完成后再展开为 chunk 列表，保持 DocumentPostProcessor 对外协议不变
        List<Document> result = new ArrayList<>();
        for (DocumentGroup group : rankedGroups) {
            result.addAll(group.documents());
        }
        // 非标准候选最后追加，避免它们抢占有明确来源的知识库文档顺序
        result.addAll(withoutDocumentId);
        return result;
    }

    /**
     * 构造文件级候选组
     *
     * @param documents            同一文件下的候选 chunk
     * @param maxChunksPerDocument 单文件最大 chunk 数
     * @return 文件级候选组
     */
    private DocumentGroup documentGroup(List<Document> documents, int maxChunksPerDocument) {
        // 同一文件内优先保留分数最高的 chunk
        // 单文件 chunk 数过多会挤占其他文件的上下文空间
        List<Document> rankedDocuments = documents.stream()
                .sorted(Comparator.comparingDouble(this::scoreOrZero).reversed())
                .limit(maxChunksPerDocument)
                .toList();
        // bestScore 表示文件级可信度，用于和其他文件比较排序
        // 这里取保留下来的最高分，避免低分 chunk 拉低整个文件的排序
        double bestScore = rankedDocuments.stream()
                .mapToDouble(this::scoreOrZero)
                .max()
                .orElse(0.0);
        return new DocumentGroup(rankedDocuments, bestScore);
    }

    /**
     * 安全读取 metadata 字段
     *
     * @param document 文档
     * @param key      metadata key
     * @return 字符串字段值
     */
    private String metadataValue(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value == null ? null : value.toString();
    }

    /**
     * 将缺失 score 的 Document 作为 0 分处理
     *
     * @param document 文档
     * @return 可排序分数
     */
    private double scoreOrZero(Document document) {
        return document.getScore() == null ? 0.0 : document.getScore();
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

    /**
     * 文件级候选组
     *
     * <p>
     * documents 是同一文件中最终允许进入上下文的 chunk
     * bestScore 是该文件参与跨文件排序的最高可信分
     *
     * @param documents 同一文件下保留的候选 chunk
     * @param bestScore 文件最高可信分
     */
    private record DocumentGroup(List<Document> documents, double bestScore) {
    }
}
