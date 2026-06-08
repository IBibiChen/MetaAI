package com.metax.rag.etl.resource;

import com.metax.rag.config.RagProperties;
import com.metax.rag.etl.model.DocumentSourceType;
import com.metax.rag.indexing.DocumentIndexingRequest;
import com.metax.rag.storage.DocumentStorageService;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
        MetaDocumentResourceFactory factory = factory(Path.of("D:/meta-ai/knowledge"));
        DocumentIndexingRequest request = request(DocumentSourceType.OBJECT_STORAGE, null,
                "bucket", "knowledge/demo.md", null);

        MetaDocumentResource resource = factory.create(request);

        assertThat(resource.documentType()).isEqualTo("markdown");
        assertThat(resource.source()).isEqualTo("knowledge/demo.md");
        assertThat(resource.resource().getFilename()).isEqualTo("demo.md");
    }

    /**
     * 本地文件路径必须限制在 local-root 下
     */
    @Test
    void shouldRejectLocalPathTraversal() throws Exception {
        Path localRoot = Files.createTempDirectory("meta-rag-local-root");
        MetaDocumentResourceFactory factory = factory(localRoot);
        DocumentIndexingRequest request = request(DocumentSourceType.LOCAL_FILE, null,
                null, null, "../secret.md");

        assertThatThrownBy(() -> factory.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("local-root");
    }

    /**
     * 本地文件应创建 FileSystemResource 并自动识别类型
     */
    @Test
    void shouldCreateLocalFileResource() throws Exception {
        Path localRoot = Files.createTempDirectory("meta-rag-local-root");
        Files.createDirectories(localRoot.resolve("docs"));
        Files.writeString(localRoot.resolve("docs/demo.json"), "{\"title\":\"Spring AI\"}");
        MetaDocumentResourceFactory factory = factory(localRoot);
        DocumentIndexingRequest request = request(DocumentSourceType.LOCAL_FILE, null,
                null, null, "docs/demo.json");

        MetaDocumentResource resource = factory.create(request);

        assertThat(resource.documentType()).isEqualTo("json");
        assertThat(resource.source()).isEqualTo("docs/demo.json");
        assertThat(resource.resource().exists()).isTrue();
    }

    /**
     * 本地文件不存在时应在提交前失败
     */
    @Test
    void shouldRejectMissingLocalFile() throws Exception {
        Path localRoot = Files.createTempDirectory("meta-rag-local-root");
        MetaDocumentResourceFactory factory = factory(localRoot);
        DocumentIndexingRequest request = request(DocumentSourceType.LOCAL_FILE, null,
                null, null, "docs/missing.md");

        assertThatThrownBy(() -> factory.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    /**
     * object provider 应选择对象存储实现
     */
    @Test
    void shouldSelectObjectStorageServiceByProvider() {
        RagProperties properties = properties(Path.of("D:/meta-ai/knowledge"));
        properties.getStorage().setProvider("object");

        MetaDocumentResourceFactory factory = factory(properties,
                List.of(new TestDocumentStorageService("object"),
                        new UnknownDocumentStorageService()));
        DocumentIndexingRequest request = request(DocumentSourceType.OBJECT_STORAGE, null,
                "bucket", "knowledge/demo.md", null);

        MetaDocumentResource resource = factory.create(request);

        assertThat(resource.resource()).isInstanceOf(MetaObjectStorageResource.class);
    }

    /**
     * legacy provider 应选择老系统文件服务实现
     */
    @Test
    void shouldSelectLegacyStorageServiceByProvider() {
        RagProperties properties = properties(Path.of("D:/meta-ai/knowledge"));
        properties.getStorage().setProvider("legacy");

        MetaDocumentResourceFactory factory = factory(properties,
                List.of(new TestDocumentStorageService("legacy"),
                        new UnknownDocumentStorageService()));
        DocumentIndexingRequest request = request(DocumentSourceType.OBJECT_STORAGE, null,
                "bucket", "knowledge/demo.md", null);

        MetaDocumentResource resource = factory.create(request);

        assertThat(resource.resource()).isInstanceOf(MetaObjectStorageResource.class);
    }

    /**
     * 未匹配 provider 时应直接失败
     */
    @Test
    void shouldRejectUnknownStorageProvider() {
        RagProperties properties = properties(Path.of("D:/meta-ai/knowledge"));
        properties.getStorage().setProvider("unknown");

        assertThatThrownBy(() -> factory(properties, List.of(new UnknownDocumentStorageService())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No DocumentStorageService found for provider: unknown");
    }

    /**
     * 同一 provider 存在多个存储实现时应直接失败
     */
    @Test
    void shouldRejectDuplicateStorageProvider() {
        RagProperties properties = properties(Path.of("D:/meta-ai/knowledge"));
        properties.getStorage().setProvider("object");

        assertThatThrownBy(() -> factory(properties, List.of(new TestDocumentStorageService("object"),
                new TestDocumentStorageService("object"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Multiple DocumentStorageService found for provider: object");
    }

    private MetaDocumentResourceFactory factory(Path localRoot) {
        RagProperties properties = properties(localRoot);
        return factory(properties, List.of(new TestDocumentStorageService("object")));
    }

    private MetaDocumentResourceFactory factory(RagProperties properties, List<DocumentStorageService> storageServices) {
        return new TestMetaDocumentResourceFactory(properties, storageServices, new MetaDocumentTypeResolver());
    }

    private RagProperties properties(Path localRoot) {
        RagProperties properties = new RagProperties();
        properties.getStorage().setLocalRoot(localRoot.toString());
        return properties;
    }

    private DocumentIndexingRequest request(DocumentSourceType sourceType,
                                        String documentType,
                                        String bucket,
                                        String objectKey,
                                        String localPath) {
        return DocumentIndexingRequest.builder()
                .tenantId("tenant-1")
                .kbId("kb-1")
                .documentId("doc-1")
                .visibility("PUBLIC")
                .documentType(documentType)
                .sourceType(sourceType)
                .filename(filename(objectKey, localPath))
                .bucket(bucket)
                .objectKey(objectKey)
                .localPath(localPath)
                .build();
    }

    private String filename(String objectKey, String localPath) {
        String value = objectKey != null ? objectKey : localPath;
        if (value == null) {
            return null;
        }
        String normalized = value.replace("\\", "/");
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private static class TestMetaDocumentResourceFactory extends MetaDocumentResourceFactory {

        TestMetaDocumentResourceFactory(RagProperties properties,
                                        List<DocumentStorageService> storageServices,
                                        MetaDocumentTypeResolver documentTypeResolver) {
            super(properties, storageServices, documentTypeResolver);
        }

        @Override
        protected String storageProvider(DocumentStorageService storageService) {
            if (storageService instanceof TestDocumentStorageService testStorageService) {
                return testStorageService.provider;
            }
            return super.storageProvider(storageService);
        }
    }

    private static class TestDocumentStorageService implements DocumentStorageService {

        private final String provider;

        TestDocumentStorageService(String provider) {
            this.provider = provider;
        }

        @Override
        public InputStream getObject(String bucket, String objectKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String defaultBucket() {
            return "test";
        }
    }

    private static class UnknownDocumentStorageService extends TestDocumentStorageService {

        UnknownDocumentStorageService() {
            super("");
        }
    }
}

