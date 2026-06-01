package com.metax.rag.etl.transformer;

import com.metax.rag.indexing.DocumentIndexingRequest;
import com.metax.rag.model.MetadataKeys;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
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
    public List<Document> apply(List<Document> documents) {
        return IntStream.range(0, documents.size())
                .mapToObj(index -> withChunkMetadata(documents.get(index), index))
                .toList();
    }

    private Document withChunkMetadata(Document document, int index) {
        String text = document.getText();
        String contentHash = DigestUtils.md5DigestAsHex(text.getBytes(StandardCharsets.UTF_8));
        String chunkId = "%s:%s".formatted(request.documentId(), index);
        Map<String, Object> metadata = new HashMap<>(document.getMetadata());
        metadata.put(MetadataKeys.CHUNK_ID, chunkId);
        metadata.put(MetadataKeys.CHUNK_INDEX, index);
        metadata.put(MetadataKeys.CONTENT_HASH, contentHash);
        Document enriched = Document.builder()
                .id(chunkId)
                .text(text)
                .metadata(metadata)
                .score(document.getScore())
                .build();
        enriched.setContentFormatter(document.getContentFormatter());
        return enriched;
    }
}

