package com.metax.rag.etl.resource;

import com.metax.rag.config.RagProperties;
import com.metax.rag.indexing.DocumentIndexingRequest;
import com.metax.rag.storage.DocumentStorageService;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MetaDocumentResourceFactoryTest .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
class MetaDocumentResourceFactoryTest {

    /**
     * 对象存储请求应创建对象存储 Resource 并自动识别类型
     */
    @Test
    void shouldCreateObjectStorageResource() {
        MetaDocumentResourceFactory factory = factory();
        DocumentIndexingRequest request = request(null, "bucket", "knowledge/demo.md");

        MetaDocumentResource resource = factory.create(request);

        assertThat(resource.documentType()).isEqualTo("markdown");
        assertThat(resource.source()).isEqualTo("knowledge/demo.md");
        assertThat(resource.resource().getFilename()).isEqualTo("demo.md");
    }

    /**
     * 显式 source 应优先作为引用来源
     */
    @Test
    void shouldUseExplicitSourceWhenProvided() {
        MetaDocumentResourceFactory factory = factory();
        DocumentIndexingRequest request = DocumentIndexingRequest.builder()
                .tenantId("tenant-1")
                .kbId("kb-1")
                .documentId("doc-1")
                .visibility("PUBLIC")
                .source("display/demo.md")
                .documentName("demo.md")
                .bucket("bucket")
                .objectKey("knowledge/demo.md")
                .build();

        MetaDocumentResource resource = factory.create(request);

        assertThat(resource.source()).isEqualTo("display/demo.md");
    }

    /**
     * bucket 缺失时应在提交索引前失败
     */
    @Test
    void shouldRejectBlankBucket() {
        MetaDocumentResourceFactory factory = factory();
        DocumentIndexingRequest request = request(null, null, "knowledge/demo.md");

        assertThatThrownBy(() -> factory.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bucket");
    }

    /**
     * objectKey 缺失时应在提交索引前失败
     */
    @Test
    void shouldRejectBlankObjectKey() {
        MetaDocumentResourceFactory factory = factory();
        DocumentIndexingRequest request = request(null, "bucket", null);

        assertThatThrownBy(() -> factory.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("objectKey");
    }

    private MetaDocumentResourceFactory factory() {
        return new MetaDocumentResourceFactory(new RagProperties(), new TestDocumentStorageService(),
                new MetaDocumentTypeResolver());
    }

    private DocumentIndexingRequest request(String documentType, String bucket, String objectKey) {
        return DocumentIndexingRequest.builder()
                .tenantId("tenant-1")
                .kbId("kb-1")
                .documentId("doc-1")
                .visibility("PUBLIC")
                .documentType(documentType)
                .documentName(filename(objectKey))
                .bucket(bucket)
                .objectKey(objectKey)
                .build();
    }

    private String filename(String objectKey) {
        if (objectKey == null) {
            return null;
        }
        String normalized = objectKey.replace("\\", "/");
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private static class TestDocumentStorageService implements DocumentStorageService {

        @Override
        public InputStream getObject(String bucket, String objectKey) {
            throw new UnsupportedOperationException();
        }
    }
}
