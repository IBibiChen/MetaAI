package com.metax.storage.event;

import com.metax.rag.etl.resource.MetaDocumentResource;
import com.metax.rag.indexing.DocumentIndexingContext;
import com.metax.rag.indexing.DocumentIndexingRequest;
import com.metax.rag.indexing.DocumentIndexingRun;
import com.metax.rag.indexing.DocumentIndexingService;
import com.metax.rag.indexing.DocumentIndexingSubmission;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * StorageDocumentIndexingEventListenerTest .
 *
 * <p>
 * 对象存储文档索引事件监听器测试
 */
class StorageDocumentIndexingEventListenerTest {

    /**
     * 事务提交事件到达后应启动已提交的 RAG 索引执行
     */
    @Test
    void shouldStartSubmissionWhenEventHandled() {
        DocumentIndexingService documentIndexingService = mock(DocumentIndexingService.class);
        StorageDocumentIndexingEventListener listener = new StorageDocumentIndexingEventListener(
                documentIndexingService);
        DocumentIndexingSubmission submission = submission();

        listener.handleStorageDocumentIndexingEvent(new StorageDocumentIndexingEvent(submission));

        verify(documentIndexingService).start(submission);
    }

    private DocumentIndexingSubmission submission() {
        DocumentIndexingRequest request = DocumentIndexingRequest.builder()
                .tenantId("t1")
                .kbId("kb1")
                .documentId("doc-1")
                .visibility("PUBLIC")
                .documentType("txt")
                .source("docs/demo.txt")
                .documentName("demo.txt")
                .bucket("bucket")
                .objectKey("docs/demo.txt")
                .build();
        DocumentIndexingRun run = DocumentIndexingRun.pending(request);
        DocumentIndexingContext context = new DocumentIndexingContext(request, new MetaDocumentResource(
                new ByteArrayResource("demo".getBytes()), "txt", "docs/demo.txt"));
        return new DocumentIndexingSubmission(run, context);
    }
}
