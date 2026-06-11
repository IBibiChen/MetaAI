package com.metax.rag.retrieval.postprocess;

import com.metax.rag.config.MetaRetrievalProperties;
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
        MetaRetrievalProperties.PostProcessor properties = new MetaRetrievalProperties.PostProcessor();
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
        MetaRetrievalProperties.PostProcessor properties = new MetaRetrievalProperties.PostProcessor();
        properties.setRerankEnabled(true);
        properties.setDeduplicateEnabled(false);
        DefaultDocumentPostProcessor processor = new DefaultDocumentPostProcessor(properties);

        List<Document> documents = List.of(new Document("first"), new Document("second"));

        List<Document> processed = processor.process(new Query("query"), documents);

        assertThat(processed).containsExactlyElementsOf(documents);
    }

    /**
     * 检索后处理应过滤低分 chunk 并按文件最高分聚合排序
     */
    @Test
    void shouldFilterLowScoreAndGroupByDocument() {
        MetaRetrievalProperties.PostProcessor properties = new MetaRetrievalProperties.PostProcessor();
        properties.setMinContextScore(0.68);
        properties.setMaxChunksPerDocument(1);
        properties.setMaxContextDocuments(3);
        DefaultDocumentPostProcessor processor = new DefaultDocumentPostProcessor(properties);

        List<Document> documents = List.of(
                document("doc-a-low", "doc-a", "doc-a:0", 0.60),
                document("doc-a-high", "doc-a", "doc-a:1", 0.82),
                document("doc-b-best", "doc-b", "doc-b:0", 0.91),
                document("doc-b-second", "doc-b", "doc-b:1", 0.89),
                document("doc-c", "doc-c", "doc-c:0", 0.70));

        List<Document> processed = processor.process(new Query("query"), documents);

        assertThat(processed)
                .extracting(Document::getText)
                .containsExactly("doc-b-best", "doc-a-high", "doc-c");
    }

    /**
     * 缺少 score 的候选应保守保留，兼容不返回分数的 VectorStore 实现
     */
    @Test
    void shouldKeepDocumentWithoutScoreWhenFilteringByScore() {
        MetaRetrievalProperties.PostProcessor properties = new MetaRetrievalProperties.PostProcessor();
        properties.setMinContextScore(0.68);
        properties.setMaxContextDocuments(2);
        DefaultDocumentPostProcessor processor = new DefaultDocumentPostProcessor(properties);

        Document document = Document.builder()
                .text("no-score")
                .metadata(Map.of(MetadataKeys.DOCUMENT_ID, "doc-a", MetadataKeys.CHUNK_ID, "doc-a:0"))
                .build();

        List<Document> processed = processor.process(new Query("query"), List.of(document));

        assertThat(processed).containsExactly(document);
    }

    private Document document(String text, String documentId, String chunkId, double score) {
        return Document.builder()
                .text(text)
                .metadata(Map.of(MetadataKeys.DOCUMENT_ID, documentId, MetadataKeys.CHUNK_ID, chunkId))
                .score(score)
                .build();
    }
}
