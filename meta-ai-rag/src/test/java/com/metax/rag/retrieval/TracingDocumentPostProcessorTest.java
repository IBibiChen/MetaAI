package com.metax.rag.retrieval;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TracingDocumentPostProcessorTest .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/1
 */
class TracingDocumentPostProcessorTest {

    /**
     * 检索后处理 trace 应记录召回数量、使用数量和耗时
     */
    @Test
    void shouldRecordPostProcessTrace() {
        RetrievalTrace.Builder builder = RetrievalTrace.builder("query");
        Map<String, Object> context = new HashMap<>();
        context.put(RetrievalTrace.CONTEXT_KEY, builder);
        Query query = Query.builder()
                .text("query")
                .context(context)
                .build();
        TracingDocumentPostProcessor processor = new TracingDocumentPostProcessor((q, documents) -> documents.subList(0, 1),
                5, 0.5, null);

        processor.process(query, List.of(new Document("a"), new Document("b")));

        RetrievalTrace trace = builder.build();
        assertThat(trace.retrievedCount()).isEqualTo(2);
        assertThat(trace.usedCount()).isEqualTo(1);
        assertThat(trace.topK()).isEqualTo(5);
        assertThat(trace.similarityThreshold()).isEqualTo(0.5);
        assertThat(trace.timings()).containsKey("postProcess");
    }
}
