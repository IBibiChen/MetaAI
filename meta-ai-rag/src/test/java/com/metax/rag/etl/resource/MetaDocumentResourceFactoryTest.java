package com.metax.rag.etl.resource;

import com.metax.rag.config.RagProperties;
import com.metax.rag.etl.model.DocumentSourceType;
import com.metax.rag.indexing.DocumentIndexingRequest;
import com.metax.rag.model.EmbeddingProvider;
import com.metax.rag.model.VectorStoreBackend;
import com.metax.rag.storage.DocumentStorageService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

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

    private MetaDocumentResourceFactory factory(Path localRoot) {
        RagProperties properties = new RagProperties();
        properties.getStorage().setLocalRoot(localRoot.toString());
        return new MetaDocumentResourceFactory(properties, mock(DocumentStorageService.class),
                new MetaDocumentTypeResolver());
    }

    private DocumentIndexingRequest request(DocumentSourceType sourceType,
                                        String documentType,
                                        String bucket,
                                        String objectKey,
                                        String localPath) {
        return new DocumentIndexingRequest(EmbeddingProvider.DASHSCOPE, VectorStoreBackend.REDIS,
                "tenant-1", "kb-1", "doc-1", documentType, sourceType, null, bucket, objectKey, localPath);
    }
}

