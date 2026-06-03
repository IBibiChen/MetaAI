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
                .containsEntry(MetadataKeys.VISIBILITY, "PUBLIC")
                .containsEntry(MetadataKeys.DEPT_ID, "")
                .containsEntry(MetadataKeys.USER_ID, "")
                .containsEntry(MetadataKeys.DOCUMENT_ID, "doc-1")
                .containsEntry(MetadataKeys.DOCUMENT_TYPE, "markdown")
                .containsEntry(MetadataKeys.SOURCE, "docs/demo.md")
                .containsEntry(MetadataKeys.FILENAME, "demo.md")
                .containsKey(MetadataKeys.CREATED_AT);
    }

    /**
     * 部门和用户权限 metadata 应按 visibility 写入
     */
    @Test
    void shouldAppendPermissionMetadata() {
        DocumentIndexingRequest deptRequest = requestBuilder()
                .visibility("DEPT")
                .deptId("dept-1")
                .build();
        DocumentIndexingRequest userRequest = requestBuilder()
                .documentId("doc-2")
                .visibility("USER")
                .userId("user-1")
                .build();

        Document deptDocument = new MetaDocumentMetadataTransformer(deptRequest)
                .transform(List.of(new Document("document text")))
                .get(0);
        Document userDocument = new MetaDocumentMetadataTransformer(userRequest)
                .transform(List.of(new Document("document text")))
                .get(0);

        assertThat(deptDocument.getMetadata())
                .containsEntry(MetadataKeys.VISIBILITY, "DEPT")
                .containsEntry(MetadataKeys.DEPT_ID, "dept-1")
                .containsEntry(MetadataKeys.USER_ID, "");
        assertThat(userDocument.getMetadata())
                .containsEntry(MetadataKeys.VISIBILITY, "USER")
                .containsEntry(MetadataKeys.DEPT_ID, "")
                .containsEntry(MetadataKeys.USER_ID, "user-1");
    }

    /**
     * 可选 metadata 字段无值时应写入空字符串
     */
    @Test
    void shouldAppendEmptyOptionalMetadata() {
        DocumentIndexingRequest request = requestBuilder()
                .filename(null)
                .build();

        Document document = new MetaDocumentMetadataTransformer(request)
                .transform(List.of(new Document("document text")))
                .get(0);

        assertThat(document.getMetadata())
                .containsEntry(MetadataKeys.DEPT_ID, "")
                .containsEntry(MetadataKeys.USER_ID, "")
                .containsEntry(MetadataKeys.FILENAME, "");
    }

    private DocumentIndexingRequest request() {
        return requestBuilder().build();
    }

    private DocumentIndexingRequest.DocumentIndexingRequestBuilder requestBuilder() {
        return DocumentIndexingRequest.builder()
                .tenantId("tenant-1")
                .knowledgeBaseId("kb-1")
                .documentId("doc-1")
                .visibility("PUBLIC")
                .documentType("markdown")
                .sourceType(DocumentSourceType.OBJECT_STORAGE)
                .source("docs/demo.md")
                .filename("demo.md")
                .bucket("bucket")
                .objectKey("object");
    }
}

