package com.metax.rag.etl.transformer;

import com.metax.rag.etl.model.DocumentSourceType;
import com.metax.rag.indexing.DocumentIndexingRequest;
import com.metax.rag.model.MetadataKeys;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MetaDocumentMetadataTransformerTest .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
class MetaDocumentMetadataTransformerTest {

    /**
     * 文档级 metadata 应补齐统一过滤字段
     */
    @Test
    void shouldAppendDocumentMetadata() {
        DocumentIndexingRequest request = request();
        MetaDocumentMetadataTransformer transformer = new MetaDocumentMetadataTransformer(request);

        List<Document> documents = transformer.transform(List.of(new Document("document text")));

        assertThat(documents).hasSize(1);
        Document document = documents.get(0);
        assertThat(document.getMetadata())
                .containsEntry(MetadataKeys.TENANT_ID, "tenant-1")
                .containsEntry(MetadataKeys.KNOWLEDGE_BASE_ID, "kb-1")
                .containsEntry(MetadataKeys.DOCUMENT_ID, "doc-1")
                .containsEntry(MetadataKeys.DOCUMENT_TYPE, "markdown")
                .containsEntry(MetadataKeys.SOURCE, "docs/demo.md")
                .containsKey(MetadataKeys.CREATED_AT);
    }

    private DocumentIndexingRequest request() {
        return new DocumentIndexingRequest("tenant-1", "kb-1", "doc-1",
                "markdown", DocumentSourceType.OBJECT_STORAGE,
                "docs/demo.md", "bucket", "object", null);
    }
}

