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
     * content formatter 应排除技术 metadata 并保留可读来源字段
     */
    @Test
    void shouldExcludeTechnicalMetadataFromFormattedContent() {
        MetaDocumentTransformerFactory factory = new MetaDocumentTransformerFactory(new RagProperties());
        DocumentTransformer transformer = factory.contentFormatTransformer();
        Document document = Document.builder()
                .text("chunk text")
                .metadata(Map.of(
                        MetadataKeys.TENANT_ID, "tenant-1",
                        MetadataKeys.KNOWLEDGE_BASE_ID, "kb-1",
                        MetadataKeys.DOCUMENT_ID, "doc-1",
                        MetadataKeys.DOCUMENT_TYPE, "markdown",
                        MetadataKeys.SOURCE, "docs/demo.md",
                        MetadataKeys.CHUNK_ID, "doc-1:0",
                        MetadataKeys.CHUNK_INDEX, 0,
                        MetadataKeys.CONTENT_HASH, "hash",
                        MetadataKeys.CREATED_AT, 1710000000000L
                ))
                .build();

        List<Document> documents = transformer.transform(List.of(document));
        String formatted = documents.get(0).getContentFormatter().format(documents.get(0), MetadataMode.EMBED);

        assertThat(formatted)
                .contains("chunk text")
                .contains("documentType: markdown")
                .contains("source: docs/demo.md")
                .doesNotContain("tenantId")
                .doesNotContain("knowledgeBaseId")
                .doesNotContain("documentId")
                .doesNotContain("chunkId")
                .doesNotContain("chunkIndex")
                .doesNotContain("contentHash")
                .doesNotContain("createdAt");
    }
}
