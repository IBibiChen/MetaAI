package com.metax.rag.pipeline;

import com.metax.rag.etl.model.DocumentSourceType;
import com.metax.rag.indexing.DocumentIndexingRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.document.DocumentWriter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * MetaEtlUpsertPipelineTest .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/1
 */
class MetaEtlUpsertPipelineTest {

    /**
     * execute 应按 read、transform、delete、write 的顺序执行
     */
    @Test
    void shouldExecuteUpsertPipelineInOrder() {
        List<String> steps = new ArrayList<>();
        DocumentReader reader = () -> {
            steps.add("read");
            return List.of(new Document("raw"));
        };
        DocumentTransformer firstTransformer = documents -> {
            steps.add("transform-1");
            return List.of(new Document("first"));
        };
        DocumentTransformer secondTransformer = documents -> {
            steps.add("transform-2");
            return List.of(new Document("second"));
        };
        TestVectorStore vectorStore = new TestVectorStore(steps);
        MetaVectorStoreSink sink = new MetaVectorStoreSink(vectorStore, mock(Filter.Expression.class));
        MetaEtlUpsertPipeline pipeline = new MetaEtlUpsertPipeline(request(), reader,
                List.of(firstTransformer, secondTransformer), List.of(), sink);

        MetaEtlPipelineResult result = pipeline.execute();

        assertThat(steps).containsExactly("read", "transform-1", "transform-2", "delete", "write");
        assertThat(result.chunkCount()).isEqualTo(1);
        assertThat(vectorStore.writtenDocuments()).hasSize(1);
    }

    /**
     * snapshot writer 应在 transform 之后、向量库 upsert 之前执行
     */
    @Test
    void shouldExecuteSnapshotWriterBeforeVectorStoreUpsert() {
        List<String> steps = new ArrayList<>();
        DocumentReader reader = () -> {
            steps.add("read");
            return List.of(new Document("raw"));
        };
        DocumentTransformer transformer = documents -> {
            steps.add("transform");
            return List.of(new Document("chunk"));
        };
        DocumentWriter snapshotWriter = documents -> steps.add("snapshot");
        TestVectorStore vectorStore = new TestVectorStore(steps);
        MetaVectorStoreSink sink = new MetaVectorStoreSink(vectorStore, mock(Filter.Expression.class));
        MetaEtlUpsertPipeline pipeline = new MetaEtlUpsertPipeline(request(), reader,
                List.of(transformer), List.of(snapshotWriter), sink);

        MetaEtlPipelineResult result = pipeline.execute();

        assertThat(steps).containsExactly("read", "transform", "snapshot", "delete", "write");
        assertThat(result.chunkCount()).isEqualTo(1);
    }

    private static class TestVectorStore implements VectorStore {

        private final List<String> steps;

        private List<Document> writtenDocuments = List.of();

        TestVectorStore(List<String> steps) {
            this.steps = steps;
        }

        @Override
        public void add(List<Document> documents) {
            steps.add("write");
            this.writtenDocuments = documents;
        }

        @Override
        public void delete(List<String> idList) {
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
            steps.add("delete");
        }

        @Override
        public List<Document> similaritySearch(org.springframework.ai.vectorstore.SearchRequest request) {
            return List.of();
        }

        List<Document> writtenDocuments() {
            return writtenDocuments;
        }
    }

    private DocumentIndexingRequest request() {
        return DocumentIndexingRequest.builder()
                .tenantId("tenant-1")
                .knowledgeBaseId("kb-1")
                .documentId("doc-1")
                .visibility("PUBLIC")
                .documentType("markdown")
                .sourceType(DocumentSourceType.OBJECT_STORAGE)
                .source("docs/demo.md")
                .filename("demo.md")
                .bucket("bucket")
                .objectKey("object")
                .build();
    }
}
