package com.metax.rag.pipeline;

import com.metax.rag.config.RagProperties;
import com.metax.rag.etl.model.DocumentSourceType;
import com.metax.rag.etl.reader.JsonDocumentReaderStrategy;
import com.metax.rag.etl.reader.MarkdownDocumentReaderStrategy;
import com.metax.rag.etl.reader.MetaDocumentReaderFactory;
import com.metax.rag.etl.reader.TextDocumentReaderStrategy;
import com.metax.rag.etl.reader.TikaDocumentReaderStrategy;
import com.metax.rag.etl.resource.MetaDocumentResource;
import com.metax.rag.etl.transformer.MetaDocumentTransformerFactory;
import com.metax.rag.indexing.DocumentIndexingContext;
import com.metax.rag.indexing.DocumentIndexingRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * MetaEtlPipelineFactoryTest .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/1
 */
class MetaEtlPipelineFactoryTest {

    /**
     * factory 应组装完整 indexing pipeline
     */
    @Test
    void shouldCreateIndexingPipeline() throws Exception {
        Path localRoot = Files.createTempDirectory("meta-rag-pipeline-root");
        Files.createDirectories(localRoot.resolve("docs"));
        Files.writeString(localRoot.resolve("docs/demo.txt"), "Spring AI RAG");
        RagProperties properties = new RagProperties();
        properties.getStorage().setLocalRoot(localRoot.toString());
        VectorStore vectorStore = mock(VectorStore.class);
        MetaEtlPipelineFactory factory = new MetaEtlPipelineFactory(
                properties,
                vectorStore,
                readerFactory(),
                new MetaDocumentTransformerFactory(properties)
        );
        DocumentIndexingContext context = new DocumentIndexingContext(request(),
                new MetaDocumentResource(new FileSystemResource(localRoot.resolve("docs/demo.txt")),
                        "txt", "docs/demo.txt"));

        MetaEtlUpsertPipeline pipeline = factory.createIndexingPipeline(context);

        assertThat(pipeline.request()).isEqualTo(request());
        assertThat(pipeline.reader()).isNotNull();
        assertThat(pipeline.transformers()).hasSize(4);
        assertThat(pipeline.snapshotWriters()).isEmpty();
        assertThat(pipeline.sink().vectorStore()).isSameAs(vectorStore);
        assertThat(pipeline.sink().deleteFilter().toString())
                .contains("tenantId")
                .contains("kbId")
                .contains("documentId");
    }

    /**
     * snapshot 开启时 factory 应组装快照 DocumentWriter
     */
    @Test
    void shouldCreateSnapshotWriterWhenEnabled() throws Exception {
        Path localRoot = Files.createTempDirectory("meta-rag-pipeline-root");
        Files.createDirectories(localRoot.resolve("docs"));
        Files.writeString(localRoot.resolve("docs/demo.txt"), "Spring AI RAG");
        RagProperties properties = new RagProperties();
        properties.getStorage().setLocalRoot(localRoot.toString());
        properties.getSnapshot().setEnabled(true);
        properties.getSnapshot().setOutputDir(Files.createTempDirectory("meta-rag-snapshot").toString());
        VectorStore vectorStore = mock(VectorStore.class);
        MetaEtlPipelineFactory factory = new MetaEtlPipelineFactory(
                properties,
                vectorStore,
                readerFactory(),
                new MetaDocumentTransformerFactory(properties)
        );
        DocumentIndexingContext context = new DocumentIndexingContext(request(),
                new MetaDocumentResource(new FileSystemResource(localRoot.resolve("docs/demo.txt")),
                        "txt", "docs/demo.txt"));

        MetaEtlUpsertPipeline pipeline = factory.createIndexingPipeline(context);

        assertThat(pipeline.snapshotWriters()).hasSize(1);
    }

    private MetaDocumentReaderFactory readerFactory() {
        TikaDocumentReaderStrategy tika = new TikaDocumentReaderStrategy();
        return new MetaDocumentReaderFactory(List.of(
                new JsonDocumentReaderStrategy(),
                new TextDocumentReaderStrategy(),
                new MarkdownDocumentReaderStrategy(),
                tika
        ), tika);
    }

    private DocumentIndexingRequest request() {
        return DocumentIndexingRequest.builder()
                .tenantId("tenant-1")
                .kbId("kb-1")
                .documentId("doc-1")
                .visibility("PUBLIC")
                .documentType("txt")
                .sourceType(DocumentSourceType.LOCAL_FILE)
                .source("docs/demo.txt")
                .documentName("demo.txt")
                .localPath("docs/demo.txt")
                .build();
    }
}
