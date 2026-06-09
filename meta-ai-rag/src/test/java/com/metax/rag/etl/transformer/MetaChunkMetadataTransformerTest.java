package com.metax.rag.etl.transformer;

import com.metax.rag.indexing.DocumentIndexingRequest;
import com.metax.rag.model.MetadataKeys;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
        DocumentIndexingRequest request = DocumentIndexingRequest.builder()
                .tenantId("tenant-1")
                .kbId("kb-1")
                .documentId("doc-1")
                .visibility("PUBLIC")
                .documentType("markdown")
                .source("docs/demo.md")
                .documentName("demo.md")
                .bucket("bucket")
                .objectKey("object")
                .build();
        MetaChunkMetadataTransformer transformer = new MetaChunkMetadataTransformer(request);

        List<Document> chunks = transformer.transform(List.of(new Document("chunk text", Map.of(MetadataKeys.DOCUMENT_ID, "doc-1"))));
        List<Document> repeatedChunks = transformer.transform(List.of(new Document("chunk text",
                Map.of(MetadataKeys.DOCUMENT_ID, "doc-1"))));

        assertThat(chunks).hasSize(1);
        Document chunk = chunks.get(0);
        assertThatCode(() -> UUID.fromString(chunk.getId())).doesNotThrowAnyException();
        assertThat(chunk.getId()).isEqualTo(repeatedChunks.get(0).getId());
        assertThat(chunk.getMetadata())
                .containsEntry(MetadataKeys.DOCUMENT_ID, "doc-1")
                .containsEntry(MetadataKeys.CHUNK_ID, "doc-1:0")
                .containsEntry(MetadataKeys.CHUNK_INDEX, 0)
                .containsKey(MetadataKeys.CONTENT_HASH);
    }
}

