package com.metax.rag.retrieval.postprocess;

import com.metax.rag.config.RagProperties;
import com.metax.rag.model.MetadataKeys;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DefaultDocumentPostProcessorTest .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/1
 */
class DefaultDocumentPostProcessorTest {

    /**
     * 检索后处理应按 chunkId 去重并限制上下文文档数量
     */
    @Test
    void shouldDeduplicateAndLimitDocuments() {
        RagProperties.PostProcessor properties = new RagProperties.PostProcessor();
        properties.setMaxContextDocuments(2);
        DefaultDocumentPostProcessor processor = new DefaultDocumentPostProcessor(properties);

        List<Document> documents = List.of(
                new Document("a", Map.of(MetadataKeys.CHUNK_ID, "doc-1:0")),
                new Document("duplicate", Map.of(MetadataKeys.CHUNK_ID, "doc-1:0")),
                new Document("b", Map.of(MetadataKeys.CHUNK_ID, "doc-1:1")),
                new Document("c", Map.of(MetadataKeys.CHUNK_ID, "doc-1:2")));

        List<Document> processed = processor.process(new Query("query"), documents);

        assertThat(processed)
                .hasSize(2)
                .extracting(Document::getText)
                .containsExactly("a", "b");
    }

    /**
     * 启用 rerank 预留开关时 NoOp 实现不应改变文档顺序
     */
    @Test
    void shouldKeepOrderWhenNoOpRerankEnabled() {
        RagProperties.PostProcessor properties = new RagProperties.PostProcessor();
        properties.setRerankEnabled(true);
        properties.setDeduplicateEnabled(false);
        DefaultDocumentPostProcessor processor = new DefaultDocumentPostProcessor(properties);

        List<Document> documents = List.of(new Document("first"), new Document("second"));

        List<Document> processed = processor.process(new Query("query"), documents);

        assertThat(processed).containsExactlyElementsOf(documents);
    }
}
