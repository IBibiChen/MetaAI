package com.metax.rag.etl.writer;

import com.metax.rag.config.MetaRetrievalProperties;
import com.metax.rag.indexing.DocumentIndexingRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MetaDocumentSnapshotWriterTest .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/1
 */
class MetaDocumentSnapshotWriterTest {

    /**
     * 快照 Writer 应把 transformed Document 写入本地文件
     */
    @Test
    void shouldWriteDocumentsToSnapshotFile() throws Exception {
        Path outputDir = Files.createTempDirectory("meta-rag-snapshot");
        MetaRetrievalProperties.Snapshot properties = new MetaRetrievalProperties.Snapshot();
        properties.setOutputDir(outputDir.toString());
        properties.setWithDocumentMarkers(true);
        properties.setMetadataMode(MetadataMode.NONE);
        MetaDocumentSnapshotWriter writer = new MetaDocumentSnapshotWriter(request(), properties);

        writer.write(List.of(new Document("chunk content")));

        Path snapshotFile = writer.createSnapshotFile();
        assertThat(snapshotFile).exists();
        assertThat(Files.readString(snapshotFile)).contains("chunk content");
    }

    /**
     * 快照文件名应清理特殊字符并限制在输出目录内
     */
    @Test
    void shouldCreateSafeSnapshotFileName() throws Exception {
        Path outputDir = Files.createTempDirectory("meta-rag-snapshot");
        MetaRetrievalProperties.Snapshot properties = new MetaRetrievalProperties.Snapshot();
        properties.setOutputDir(outputDir.toString());
        MetaDocumentSnapshotWriter writer = new MetaDocumentSnapshotWriter(unsafeRequest(), properties);

        Path snapshotFile = writer.createSnapshotFile();

        assertThat(snapshotFile.toString()).startsWith(outputDir.toAbsolutePath().normalize().toString());
        assertThat(snapshotFile.getFileName().toString()).doesNotContain("..", "/", "\\");
    }

    private DocumentIndexingRequest request() {
        return DocumentIndexingRequest.builder()
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
    }

    private DocumentIndexingRequest unsafeRequest() {
        return DocumentIndexingRequest.builder()
                .tenantId("../tenant")
                .kbId("kb/1")
                .documentId("doc:1")
                .visibility("PUBLIC")
                .documentType("markdown")
                .source("docs/demo.md")
                .documentName("demo.md")
                .bucket("bucket")
                .objectKey("object")
                .build();
    }
}
