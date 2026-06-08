package com.metax.rag.etl.transformer;

import com.metax.rag.config.RagProperties;
import com.metax.rag.model.MetadataKeys;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.document.MetadataMode;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MetaDocumentTransformerFactoryTest .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/1
 */
class MetaDocumentTransformerFactoryTest {

    /**
     * tokenTextSplitter 应使用 RAG chunk 配置创建 TokenTextSplitter
     */
    @Test
    void shouldCreateSplitterByRagProperties() {
        RagProperties properties = new RagProperties();
        properties.getChunk().setSize(20);
        properties.getChunk().setMinChars(1);
        properties.getChunk().setMinLengthToEmbed(1);
        properties.getChunk().setMaxNumChunks(10);
        MetaDocumentTransformerFactory factory = new MetaDocumentTransformerFactory(properties);
        DocumentTransformer splitter = factory.tokenTextSplitter();
        Document document = Document.builder()
                .text("Spring AI helps build RAG applications. TokenTextSplitter splits long text into chunks.")
                .build();

        List<Document> chunks = splitter.transform(List.of(document));

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getText()).isNotBlank());
    }

    /**
     * content formatter 应排除技术 metadata
     */
    @Test
    void shouldExcludeTechnicalMetadataFromFormattedContent() {
        MetaDocumentTransformerFactory factory = new MetaDocumentTransformerFactory(new RagProperties());
        DocumentTransformer transformer = factory.contentFormatTransformer();
        Document document = Document.builder()
                .text("chunk text")
                .metadata(Map.ofEntries(
                        Map.entry(MetadataKeys.SCOPE, MetadataKeys.SCOPE_SESSION),
                        Map.entry(MetadataKeys.TENANT_ID, "tenant-1"),
                        Map.entry(MetadataKeys.KB_ID, "kb-1"),
                        Map.entry(MetadataKeys.DOCUMENT_ID, "doc-1"),
                        Map.entry(MetadataKeys.CHAT_ID, "c1"),
                        Map.entry(MetadataKeys.FILE_ID, "file-1"),
                        Map.entry(MetadataKeys.DOCUMENT_TYPE, "markdown"),
                        Map.entry(MetadataKeys.SOURCE, "docs/demo.md"),
                        Map.entry(MetadataKeys.CHUNK_ID, "doc-1:0"),
                        Map.entry(MetadataKeys.CHUNK_INDEX, 0),
                        Map.entry(MetadataKeys.CONTENT_HASH, "hash"),
                        Map.entry(MetadataKeys.CREATED_AT, 1710000000000L)
                ))
                .build();

        List<Document> documents = transformer.transform(List.of(document));
        String formatted = documents.get(0).getContentFormatter().format(documents.get(0), MetadataMode.EMBED);

        assertThat(formatted)
                .contains("chunk text")
                .doesNotContain("scope")
                .doesNotContain("tenantId")
                .doesNotContain("kbId")
                .doesNotContain("documentId")
                .doesNotContain("chatId")
                .doesNotContain("fileId")
                .doesNotContain("documentType")
                .doesNotContain("source")
                .doesNotContain("chunkId")
                .doesNotContain("chunkIndex")
                .doesNotContain("contentHash")
                .doesNotContain("createdAt");
    }
}
