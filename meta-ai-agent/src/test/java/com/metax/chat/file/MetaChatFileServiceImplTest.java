package com.metax.chat.file;

import com.metax.chat.file.event.MetaChatFileIndexingEvent;
import com.metax.rag.etl.resource.MetaDocumentTypeResolver;
import com.metax.rag.storage.ObjectStorageClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MetaChatFileServiceImplTest .
 *
 * <p>
 * 聊天文件索引 metadata 单元测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
class MetaChatFileServiceImplTest {

    @Test
    void uploadAndSubmitIndexShouldReturnUploadedStatusWithoutWritingVectorStore() {
        ObjectStorageClient objectStorageClient = mock(ObjectStorageClient.class);
        VectorStore vectorStore = mock(VectorStore.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        when(objectStorageClient.defaultBucket()).thenReturn("meta-ai");
        when(objectStorageClient.putObject(eq("meta-ai"), any(), any(InputStream.class), eq(4L), eq("application/pdf")))
                .thenReturn(null);
        MetaChatFileServiceImpl service = new TestMetaChatFileServiceImpl(objectStorageClient,
                new MetaDocumentTypeResolver(), vectorStore, eventPublisher);
        MockMultipartFile file = new MockMultipartFile("files", "demo.pdf", "application/pdf",
                "test".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        List<MetaChatFileItemResponse> responses = service.uploadAndSubmitIndex("t1", "u1", "t1-u1-s1",
                new MockMultipartFile[]{file});

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).parseStatus()).isEqualTo(MetaChatFileStatus.UPLOADED.name());
        verify(vectorStore, never()).write(any());
        verify(vectorStore, never()).delete(org.mockito.ArgumentMatchers.any(Filter.Expression.class));
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.argThat(event ->
                event instanceof MetaChatFileIndexingEvent indexingEvent
                        && indexingEvent.getFileId().equals(responses.get(0).fileId())));
    }

    private static final class TestMetaChatFileServiceImpl extends MetaChatFileServiceImpl {

        private TestMetaChatFileServiceImpl(ObjectStorageClient objectStorageClient,
                                            MetaDocumentTypeResolver documentTypeResolver,
                                            VectorStore vectorStore,
                                            ApplicationEventPublisher eventPublisher) {
            super(objectStorageClient, documentTypeResolver, vectorStore, eventPublisher);
        }

        @Override
        public boolean save(MetaChatFileDO entity) {
            entity.setId(1L);
            return true;
        }

        @Override
        public boolean updateById(MetaChatFileDO entity) {
            return true;
        }

    }
}
