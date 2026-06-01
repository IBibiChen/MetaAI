package com.metax.rag.etl.transformer;

import com.metax.rag.etl.model.DocumentSourceType;
import com.metax.rag.indexing.DocumentIndexingRequest;
import com.metax.rag.model.MetadataKeys;
import com.metax.rag.model.EmbeddingProvider;
import com.metax.rag.model.VectorStoreBackend;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MetaChunkMetadataTransformerTest .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
class MetaChunkMetadataTransformerTest {

    /**
     * chunk metadata 应补齐稳定 chunk ID 和内容 hash
     */
    @Test
    void shouldAppendChunkMetadata() {
        DocumentIndexingRequest request = new DocumentIndexingRequest(EmbeddingProvider.DASHSCOPE, VectorStoreBackend.REDIS,
                "tenant-1", "kb-1", "doc-1", "markdown", DocumentSourceType.OBJECT_STORAGE,
                "docs/demo.md", "bucket", "object", null);
        MetaChunkMetadataTransformer transformer = new MetaChunkMetadataTransformer(request);

        List<Document> chunks = transformer.transform(List.of(new Document("chunk text", Map.of(MetadataKeys.DOCUMENT_ID, "doc-1"))));

        assertThat(chunks).hasSize(1);
        Document chunk = chunks.get(0);
        assertThat(chunk.getId()).isEqualTo("doc-1:0");
        assertThat(chunk.getMetadata())
                .containsEntry(MetadataKeys.DOCUMENT_ID, "doc-1")
                .containsEntry(MetadataKeys.CHUNK_ID, "doc-1:0")
                .containsEntry(MetadataKeys.CHUNK_INDEX, 0)
                .containsKey(MetadataKeys.CONTENT_HASH);
    }
}

