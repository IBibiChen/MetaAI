package com.metax.rag.pipeline;

import com.metax.rag.config.MetaRetrievalProperties;
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
import org.springframework.core.io.ByteArrayResource;

import java.nio.file.Files;
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
        MetaRetrievalProperties properties = new MetaRetrievalProperties();
        VectorStore vectorStore = mock(VectorStore.class);
        MetaVectorStoreWriter vectorStoreWriter = new MetaVectorStoreWriter(vectorStore, properties);
        MetaEtlPipelineFactory factory = new MetaEtlPipelineFactory(
                properties,
                vectorStoreWriter,
                readerFactory(),
                new MetaDocumentTransformerFactory(properties)
        );
        DocumentIndexingContext context = new DocumentIndexingContext(request(),
                new MetaDocumentResource(resource(), "txt", "docs/demo.txt"));

        MetaEtlUpsertPipeline pipeline = factory.createIndexingPipeline(context);

        assertThat(pipeline.request()).isEqualTo(request());
        assertThat(pipeline.reader()).isNotNull();
        assertThat(pipeline.transformers()).hasSize(4);
        assertThat(pipeline.snapshotWriters()).isEmpty();
        assertThat(pipeline.sink().vectorStoreWriter()).isSameAs(vectorStoreWriter);
        assertThat(pipeline.sink().deleteFilter().toString())
                .contains("scope")
                .contains("knowledge")
                .contains("tenantId")
                .contains("kbId")
                .contains("documentId");
    }

    /**
     * snapshot 开启时 factory 应组装快照 DocumentWriter
     */
    @Test
    void shouldCreateSnapshotWriterWhenEnabled() throws Exception {
        MetaRetrievalProperties properties = new MetaRetrievalProperties();
        properties.getSnapshot().setEnabled(true);
        properties.getSnapshot().setOutputDir(Files.createTempDirectory("meta-rag-snapshot").toString());
        VectorStore vectorStore = mock(VectorStore.class);
        MetaVectorStoreWriter vectorStoreWriter = new MetaVectorStoreWriter(vectorStore, properties);
        MetaEtlPipelineFactory factory = new MetaEtlPipelineFactory(
                properties,
                vectorStoreWriter,
                readerFactory(),
                new MetaDocumentTransformerFactory(properties)
        );
        DocumentIndexingContext context = new DocumentIndexingContext(request(),
                new MetaDocumentResource(resource(), "txt", "docs/demo.txt"));

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
                .source("docs/demo.txt")
                .documentName("demo.txt")
                .bucket("bucket")
                .objectKey("docs/demo.txt")
                .build();
    }

    private ByteArrayResource resource() {
        return new ByteArrayResource("Spring AI RAG".getBytes()) {

            @Override
            public String getFilename() {
                return "demo.txt";
            }
        };
    }
}
