package com.metax.rag.indexing;

import com.metax.rag.etl.resource.MetaDocumentResource;
import com.metax.rag.etl.resource.MetaDocumentResourceFactory;
import com.metax.rag.pipeline.MetaEtlPipeline;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DocumentIndexingServiceTest .
 *
 * <p>
 * RAG 文档索引门面服务测试
 */
class DocumentIndexingServiceTest {

    /**
     * submit 只应提交 run 并执行绑定回调，不启动后台索引
     */
    @Test
    void submitShouldCreateRunWithoutStartingIndexing() {
        CapturingRunRepository runRepository = new CapturingRunRepository();
        MetaEtlPipeline etlPipeline = mock(MetaEtlPipeline.class);
        DocumentIndexingService service = service(runRepository, etlPipeline);
        List<DocumentIndexingRun> boundRuns = new ArrayList<>();

        DocumentIndexingSubmission submission = service.submit(request(), boundRuns::add);

        assertThat(runRepository.savedRuns()).containsExactly(submission.run());
        assertThat(boundRuns).containsExactly(submission.run());
        verify(etlPipeline, never()).runIndexing(any(DocumentIndexingRun.class), any(DocumentIndexingContext.class));
    }

    /**
     * start 应启动已提交的后台索引
     */
    @Test
    void startShouldRunSubmittedIndexing() {
        CapturingRunRepository runRepository = new CapturingRunRepository();
        MetaEtlPipeline etlPipeline = mock(MetaEtlPipeline.class);
        DocumentIndexingService service = service(runRepository, etlPipeline);
        DocumentIndexingSubmission submission = service.submit(request(), ignored -> {
        });

        service.start(submission);

        verify(etlPipeline).runIndexing(submission.run(), submission.context());
    }

    private DocumentIndexingService service(CapturingRunRepository runRepository, MetaEtlPipeline etlPipeline) {
        MetaDocumentResourceFactory resourceFactory = mock(MetaDocumentResourceFactory.class);
        when(resourceFactory.create(any(DocumentIndexingRequest.class))).thenReturn(new MetaDocumentResource(
                new ByteArrayResource("demo".getBytes()), "txt", "docs/demo.txt"));
        return new DocumentIndexingService(runRepository, etlPipeline, resourceFactory);
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
}
