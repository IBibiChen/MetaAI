package com.metax.rag.etl.reader;

import com.metax.rag.config.MetaRetrievalProperties;
import com.metax.rag.etl.resource.MetaDocumentResource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

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

    /**
     * pdf 文档应优先委托 PaddleOCR Reader 解析扫描件
     *
     * <p>
     * 该用例验证 ReaderFactory 对 pdf documentType 的策略选择和 OCR Reader 页级 metadata
     */
    @Test
    void shouldReadPdfByPaddleOcrReader() {
        MetaDocumentReaderFactory factory = ocrFactory();
        DocumentReader reader = factory.create(resource("pdf-bytes", "demo.pdf", "pdf"));

        List<Document> documents = reader.read();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getText()).isEqualTo("第一页 OCR 文本");
        assertThat(documents.get(0).getMetadata())
                .containsEntry("source", "demo.pdf")
                .containsEntry("pageNumber", 1)
                .containsEntry("ocrProvider", "paddleocr")
                .containsEntry("ocrFileType", "pdf");
    }

    /**
     * 图片文档应优先委托 PaddleOCR Reader 解析
     *
     * <p>
     * 该用例验证 ReaderFactory 对图片 documentType 的策略选择和 OCR Reader metadata
     */
    @Test
    void shouldReadImageByPaddleOcrReader() {
        MetaDocumentReaderFactory factory = ocrFactory();
        DocumentReader reader = factory.create(resource("image-bytes", "demo.jpg", "jpg"));

        List<Document> documents = reader.read();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getText()).isEqualTo("第一页 OCR 文本");
        assertThat(documents.get(0).getMetadata())
                .containsEntry("source", "demo.jpg")
                .containsEntry("pageNumber", 1)
                .containsEntry("ocrProvider", "paddleocr")
                .containsEntry("ocrFileType", "image");
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

    /**
     * 创建带 PaddleOCR 策略的 ReaderFactory
     *
     * <p>
     * 测试中通过覆盖 PaddleOcrClient 避免真实调用 OCR HTTP 服务
     *
     * @return MetaDocumentReaderFactory
     */
    private MetaDocumentReaderFactory ocrFactory() {
        TikaDocumentReaderStrategy tika = new TikaDocumentReaderStrategy();
        return new MetaDocumentReaderFactory(List.of(
                new PaddleOcrDocumentReaderStrategy(ocrProperties(), new PaddleOcrClient(ocrProperties()) {
                    @Override
                    public List<String> recognize(Resource resource, String documentType) {
                        return List.of("第一页 OCR 文本");
                    }
                }),
                tika
        ), tika);
    }

    /**
     * 创建默认 OCR 配置
     *
     * @return MetaRetrievalProperties
     */
    private MetaRetrievalProperties ocrProperties() {
        return new MetaRetrievalProperties();
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

