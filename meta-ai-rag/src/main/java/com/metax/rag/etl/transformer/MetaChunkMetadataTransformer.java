package com.metax.rag.etl.transformer;

import com.metax.rag.indexing.DocumentIndexingRequest;
import com.metax.rag.model.MetadataKeys;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.lang.NonNull;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * MetaChunkMetadataTransformer .
 *
 * <p>
 * MetaAI chunk 级 metadata Transformer，严格实现 Spring AI DocumentTransformer 接口
 * 负责在 TokenTextSplitter 之后补齐稳定 chunk ID、chunkIndex 和 contentHash
 *
 * <p>
 * TokenTextSplitter 会写入 Spring AI 自带的 parent_document_id、chunk_index 和 total_chunks
 * 本类额外写入项目统一 metadata key，便于 Redis / Qdrant / Milvus 使用相同字段做过滤和引用展示
 *
 * <p>
 * chunk 可以理解为“文档切片”或“文本片段”
 * 一份原始文档不会整体写入 VectorStore，而是先由 TokenTextSplitter 切成多个 chunk Document
 * VectorStore 保存的是每个 chunk 的文本、向量和 metadata
 * 检索时召回的也是相关 chunk，而不是直接召回整篇原始文档
 *
 * <p>
 * chunk 切分示例
 * <pre>{@code
 * doc-001 原始文档
 *   -> chunk 0：第一段到第三段
 *   -> chunk 1：第四段到第六段
 *   -> chunk 2：第七段到第九段
 * }</pre>
 *
 * <p>
 * chunk metadata 示例
 * <pre>{@code
 * documentId=doc-001
 * chunkId=doc-001:0
 * chunkIndex=0
 * contentHash=md5
 * }</pre>
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
public class MetaChunkMetadataTransformer implements DocumentTransformer {

    private final DocumentIndexingRequest request;

    public MetaChunkMetadataTransformer(DocumentIndexingRequest request) {
        this.request = request;
    }

    /**
     * 补齐 chunk 级 metadata
     *
     * <p>
     * chunkId 使用 documentId + chunkIndex 构造，保证同一文档重复索引时 chunk ID 稳定
     * contentHash 用于判断 chunk 内容是否变化，后续可以扩展为增量写入或索引审计
     *
     * @param documents 切分后的 chunk Document 列表
     * @return 补齐 chunk metadata 的 Document 列表
     */
    @Override
    @NonNull
    public List<Document> apply(@NonNull List<Document> documents) {
        return IntStream.range(0, documents.size())
                .mapToObj(index -> withChunkMetadata(documents.get(index), index))
                .toList();
    }

    /**
     * 为切分后的文档片段补齐 chunk 级 metadata
     *
     * <p>
     * 该方法位于 TokenTextSplitter 之后，负责生成稳定 chunkId、chunkIndex 和 contentHash
     * 因为只有切分后才有 chunk 列表顺序，才能生成 chunkIndex
     *
     * @param document 切分后的 chunk Document
     * @param index    chunk 顺序
     * @return 补齐 chunk metadata 的 Document
     */
    private Document withChunkMetadata(Document document, int index) {
        String text = document.getText();
        String contentHash = hashContent(text);
        // chunkId 使用 documentId + chunkIndex，保证同一文档重复索引时 ID 稳定
        String chunkId = "%s:%s".formatted(request.documentId(), index);
        // 保留切分器和文档级 transformer 已经写入的 metadata
        Map<String, Object> metadata = new HashMap<>(document.getMetadata());
        metadata.put(MetadataKeys.CHUNK_ID, chunkId);
        // chunkIndex 使用当前列表顺序，便于还原片段顺序和前端定位
        metadata.put(MetadataKeys.CHUNK_INDEX, index);
        // contentHash 用于审计和后续增量索引能力
        metadata.put(MetadataKeys.CONTENT_HASH, contentHash);
        Document enriched = Document.builder()
                .id(chunkId)
                .text(text)
                .metadata(metadata)
                .score(document.getScore())
                .build();
        // Document builder 不会自动继承 contentFormatter，需要显式恢复
        enriched.setContentFormatter(document.getContentFormatter());
        return enriched;
    }

    /**
     * 计算 chunk 内容 hash
     *
     * <p>
     * 空文本 chunk 使用空串计算 hash，避免空指针并保持 hash 结果稳定
     *
     * @param text chunk 文本
     * @return contentHash
     */
    private String hashContent(String text) {
        String normalizedText = text == null ? "" : text;
        return DigestUtils.md5DigestAsHex(normalizedText.getBytes(StandardCharsets.UTF_8));
    }
}

