package com.metax.rag.etl.reader;

import com.metax.rag.etl.resource.MetaDocumentResource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MetaDocumentReaderTest .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
class MetaDocumentReaderTest {

    /**
     * MetaDocumentReader 应只负责委托 delegate Reader
     */
    @Test
    void shouldDelegateToSelectedReader() {
        Document expected = Document.builder().text("delegated").build();
        MetaDocumentResource resource = resource("ignored", "demo.txt", "txt");
        DocumentReader delegate = () -> List.of(expected);
        MetaDocumentReader reader = new MetaDocumentReader(resource, delegate);

        List<Document> documents = reader.read();

        assertThat(documents).containsExactly(expected);
    }

    /**
     * markdown 文档应委托 Spring AI MarkdownDocumentReader 解析
     */
    @Test
    void shouldReadMarkdownBySpringAiMarkdownReader() {
        MetaDocumentReaderFactory factory = factory();
        DocumentReader reader = factory.create(resource("# Title\n\nSpring AI supports RAG", "demo.md", "markdown"));

        List<Document> documents = reader.read();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getText()).isEqualTo("Spring AI supports RAG");
        assertThat(documents.get(0).getMetadata()).containsEntry("title", "Title");
    }

    /**
     * json 文档应复用 Spring AI JsonReader
     */
    @Test
    void shouldReadJsonBySpringAiJsonReader() {
        MetaDocumentReaderFactory factory = factory();
        DocumentReader reader = factory.create(resource("{\"title\":\"Spring AI\"}", "demo.json", "json"));

        List<Document> documents = reader.read();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getText()).contains("Spring AI");
    }

    /**
     * txt 文档应按 UTF-8 解析
     */
    @Test
    void shouldReadTextByUtf8() {
        MetaDocumentReaderFactory factory = factory();
        DocumentReader reader = factory.create(resource("Spring AI 支持 RAG", "demo.txt", "txt"));

        List<Document> documents = reader.read();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getText()).isEqualTo("Spring AI 支持 RAG");
        assertThat(documents.get(0).getMetadata()).containsEntry("source", "demo.txt");
    }

    private MetaDocumentReaderFactory factory() {
        TikaDocumentReaderStrategy tika = new TikaDocumentReaderStrategy();
        return new MetaDocumentReaderFactory(List.of(
                new JsonDocumentReaderStrategy(),
                new TextDocumentReaderStrategy(),
                new MarkdownDocumentReaderStrategy(),
                tika
        ), tika);
    }

    private MetaDocumentResource resource(String text, String filename, String documentType) {
        ByteArrayResource resource = new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        return new MetaDocumentResource(resource, documentType, filename);
    }
}

