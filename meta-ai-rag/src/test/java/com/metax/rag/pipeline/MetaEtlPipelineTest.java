package com.metax.rag.pipeline;

import com.metax.rag.indexing.DocumentIndexingContext;
import com.metax.rag.indexing.DocumentIndexingRequest;
import com.metax.rag.indexing.DocumentIndexingRun;
import com.metax.rag.indexing.DocumentIndexingRunObserver;
import com.metax.rag.indexing.DocumentIndexingRunRepository;
import com.metax.rag.indexing.DocumentIndexingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MetaEtlPipelineTest .
 *
 * <p>
 * RAG 异步文档索引调度器测试
 */
class MetaEtlPipelineTest {

    @Test
    void runIndexingShouldMarkFailedWhenPipelineThrowsException() {
        CapturingRunRepository runRepository = new CapturingRunRepository();
        CapturingRunObserver observer = new CapturingRunObserver();
        MetaEtlPipeline pipeline = pipeline(new IllegalStateException("reader failed"), runRepository, observer);
        DocumentIndexingRun run = run();

        pipeline.runIndexing(run, context());

        assertThat(runRepository.savedRuns()).extracting(DocumentIndexingRun::status)
                .containsExactly(DocumentIndexingStatus.RUNNING, DocumentIndexingStatus.FAILED);
        assertThat(observer.changedRuns()).extracting(DocumentIndexingRun::status)
                .containsExactly(DocumentIndexingStatus.RUNNING, DocumentIndexingStatus.FAILED);
        assertThat(runRepository.savedRuns().get(1).message()).isEqualTo("IllegalStateException: reader failed");
    }

    @Test
    void runIndexingShouldMarkFailedBeforeRethrowingVirtualMachineError() {
        CapturingRunRepository runRepository = new CapturingRunRepository();
        CapturingRunObserver observer = new CapturingRunObserver();
        MetaEtlPipeline pipeline = pipeline(new StackOverflowError(), runRepository, observer);
        DocumentIndexingRun run = run();

        assertThatThrownBy(() -> pipeline.runIndexing(run, context()))
                .isInstanceOf(StackOverflowError.class);

        assertThat(runRepository.savedRuns()).extracting(DocumentIndexingRun::status)
                .containsExactly(DocumentIndexingStatus.RUNNING, DocumentIndexingStatus.FAILED);
        assertThat(observer.changedRuns()).extracting(DocumentIndexingRun::status)
                .containsExactly(DocumentIndexingStatus.RUNNING, DocumentIndexingStatus.FAILED);
        assertThat(runRepository.savedRuns().get(1).message()).isEqualTo("StackOverflowError");
    }

    private MetaEtlPipeline pipeline(Throwable failure,
                                     CapturingRunRepository runRepository,
                                     CapturingRunObserver observer) {
        MetaEtlPipelineFactory pipelineFactory = mock(MetaEtlPipelineFactory.class);
        MetaEtlUpsertPipeline upsertPipeline = new MetaEtlUpsertPipeline(request(), () -> {
            sneakyThrow(failure);
            return List.of();
        }, List.of(), List.of(), mock(MetaVectorStoreSink.class));
        when(pipelineFactory.createIndexingPipeline(any(DocumentIndexingContext.class))).thenReturn(upsertPipeline);
        return new MetaEtlPipeline(pipelineFactory, runRepository, objectProvider(observer));
    }

    private ObjectProvider<DocumentIndexingRunObserver> objectProvider(DocumentIndexingRunObserver observer) {
        return new ObjectProvider<>() {

            @Override
            public DocumentIndexingRunObserver getObject(Object... args) {
                return observer;
            }

            @Override
            public DocumentIndexingRunObserver getIfAvailable() {
                return observer;
            }

            @Override
            public DocumentIndexingRunObserver getIfUnique() {
                return observer;
            }

            @Override
            public DocumentIndexingRunObserver getObject() {
                return observer;
            }

            @Override
            public Stream<DocumentIndexingRunObserver> orderedStream() {
                return Stream.of(observer);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private <E extends Throwable> void sneakyThrow(Throwable failure) throws E {
        throw (E) failure;
    }

    private DocumentIndexingRun run() {
        return DocumentIndexingRun.pending(request());
    }

    private DocumentIndexingContext context() {
        return new DocumentIndexingContext(request(), new com.metax.rag.etl.resource.MetaDocumentResource(
                new ByteArrayResource("demo".getBytes()), "txt", "docs/demo.txt"));
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

    private static class CapturingRunRepository extends DocumentIndexingRunRepository {

        private final List<DocumentIndexingRun> savedRuns = new ArrayList<>();

        CapturingRunRepository() {
            super(null, null);
        }

        @Override
        public void save(DocumentIndexingRun run) {
            savedRuns.add(run);
        }

        List<DocumentIndexingRun> savedRuns() {
            return savedRuns;
        }
    }

    private static class CapturingRunObserver implements DocumentIndexingRunObserver {

        private final List<DocumentIndexingRun> changedRuns = new ArrayList<>();

        @Override
        public void onRunChanged(DocumentIndexingRun run) {
            changedRuns.add(run);
        }

        List<DocumentIndexingRun> changedRuns() {
            return changedRuns;
        }
    }
}
