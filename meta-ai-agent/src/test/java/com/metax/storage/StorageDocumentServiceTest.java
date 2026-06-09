package com.metax.storage;

import com.metax.rag.config.MetaRetrievalProperties;
import com.metax.rag.etl.resource.MetaDocumentTypeResolver;
import com.metax.rag.indexing.DocumentIndexingRequest;
import com.metax.rag.indexing.DocumentIndexingRun;
import com.metax.rag.indexing.DocumentIndexingService;
import com.metax.rag.storage.ObjectStorageClient;
import com.metax.rag.storage.StoredObject;
import com.metax.storage.request.StorageDocumentUploadRequest;
import com.metax.storage.response.StorageDocumentDownloadResponse;
import com.metax.storage.response.StorageDocumentUploadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StorageDocumentServiceTest .
 *
 * <p>
 * 对象存储文档服务单元测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
@ExtendWith(MockitoExtension.class)
class StorageDocumentServiceTest {

    @Mock
    private ObjectStorageClient objectStorageClient;

    @Mock
    private DocumentIndexingService documentIndexingService;

    private FakeStorageDocumentService service;

    @BeforeEach
    void setUp() {
        MetaRetrievalProperties properties = new MetaRetrievalProperties();
        service = new FakeStorageDocumentService(objectStorageClient, documentIndexingService, properties,
                new MetaDocumentTypeResolver());
    }

    @Test
    void uploadShouldStoreObjectAndMetadata() {
        when(objectStorageClient.defaultBucket()).thenReturn("meta-ai-knowledge");
        when(objectStorageClient.putObject(eq("meta-ai-knowledge"), any(), any(InputStream.class), eq(11L),
                eq("text/plain"))).thenReturn(new StoredObject("meta-ai-knowledge", "key", "etag", null, 11L,
                "text/plain"));

        StorageDocumentUploadResponse response = service.upload(uploadRequest("t1", "kb1", null, null,
                null, null, false));

        assertThat(response.documentId()).isNotBlank();
        assertThat(response.originalFilename()).isEqualTo("demo.txt");
        assertThat(response.bucket()).isEqualTo("meta-ai-knowledge");
        assertThat(response.objectKey()).startsWith("storage/t1/kb1/");
        assertThat(response.fileSha256()).hasSize(64);
        assertThat(response.indexStatus()).isEqualTo(StorageDocumentIndexStatus.UPLOADED.name());
        assertThat(response.chunkCount()).isZero();
        assertThat(service.savedEntity).isNotNull();
        assertThat(service.savedEntity.getFileSize()).isEqualTo(11L);
        assertThat(service.savedEntity.getDocumentType()).isEqualTo("txt");
        assertThat(service.savedEntity.getVisibility()).isEqualTo("PUBLIC");
        assertThat(service.savedEntity.getChunkCount()).isZero();
        assertThat(response.visibility()).isEqualTo("PUBLIC");
        assertThat(response.documentType()).isEqualTo("txt");
    }

    @Test
    void uploadShouldSubmitIndexWhenAutoIndexEnabled() {
        when(objectStorageClient.defaultBucket()).thenReturn("meta-ai-knowledge");
        when(objectStorageClient.putObject(eq("meta-ai-knowledge"), any(), any(InputStream.class), eq(11L),
                eq("text/plain"))).thenReturn(new StoredObject("meta-ai-knowledge", "key", "etag", null, 11L,
                "text/plain"));
        when(documentIndexingService.submit(any(DocumentIndexingRequest.class), any())).thenAnswer(invocation -> {
            DocumentIndexingRequest request = invocation.getArgument(0);
            DocumentIndexingRun run = DocumentIndexingRun.pending(request);
            Consumer<DocumentIndexingRun> beforeRun = invocation.getArgument(1);
            beforeRun.accept(run);
            return run;
        });

        StorageDocumentUploadResponse response = service.upload(uploadRequest("t1", "kb1", "DEPT",
                "dept-1", null, "txt", true));

        ArgumentCaptor<DocumentIndexingRequest> captor = ArgumentCaptor.forClass(DocumentIndexingRequest.class);
        verify(documentIndexingService).submit(captor.capture(), any());
        assertThat(captor.getValue().tenantId()).isEqualTo("t1");
        assertThat(captor.getValue().kbId()).isEqualTo("kb1");
        assertThat(captor.getValue().visibility()).isEqualTo("DEPT");
        assertThat(captor.getValue().deptId()).isEqualTo("dept-1");
        assertThat(captor.getValue().bucket()).isEqualTo("meta-ai-knowledge");
        assertThat(captor.getValue().documentName()).isEqualTo("demo.txt");
        assertThat(response.indexStatus()).isEqualTo(StorageDocumentIndexStatus.INDEXING.name());
        assertThat(response.chunkCount()).isZero();
        assertThat(response.latestIndexingRunId()).isNotBlank();
    }

    @Test
    void downloadShouldFindDocumentByGlobalDocumentId() {
        StorageDocumentDO entity = document("doc-1");
        service.savedEntity = entity;
        when(objectStorageClient.getObject("meta-ai-knowledge", "storage/t1/kb1/demo.txt"))
                .thenReturn(new ByteArrayInputStream("hello".getBytes()));

        StorageDocumentDownloadResponse download = service.download("doc-1");

        assertThat(download.filename()).isEqualTo("demo.txt");
        assertThat(download.contentType()).isEqualTo("text/plain");
        assertThat(download.fileSize()).isEqualTo(5L);
        assertThat(download.inputStream()).isNotNull();
        verify(objectStorageClient).getObject("meta-ai-knowledge", "storage/t1/kb1/demo.txt");
    }

    @Test
    void downloadShouldFailWhenGlobalDocumentIdNotFound() {
        service.savedEntity = null;

        assertThatThrownBy(() -> service.download("doc-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("storage document not found: doc-1");
    }

    private StorageDocumentDO document(String documentId) {
        StorageDocumentDO entity = new StorageDocumentDO();
        entity.setTenantId("t1");
        entity.setKbId("kb1");
        entity.setDocumentId(documentId);
        entity.setOriginalFilename("demo.txt");
        entity.setBucket("meta-ai-knowledge");
        entity.setObjectKey("storage/t1/kb1/demo.txt");
        entity.setContentType("text/plain");
        entity.setFileSize(5L);
        entity.setDeleted(Boolean.FALSE);
        return entity;
    }

    private StorageDocumentUploadRequest uploadRequest(String tenantId,
                                                       String kbId,
                                                       String visibility,
                                                       String deptId,
                                                       String userId,
                                                       String documentType,
                                                       Boolean autoIndex) {
        StorageDocumentUploadRequest request = new StorageDocumentUploadRequest();
        request.setTenantId(tenantId);
        request.setKbId(kbId);
        request.setVisibility(visibility);
        request.setDeptId(deptId);
        request.setUserId(userId);
        request.setDocumentType(documentType);
        request.setAutoIndex(autoIndex);
        request.setFile(new MockMultipartFile("file", "demo.txt", "text/plain", "hello world".getBytes()));
        return request;
    }

    private static final class FakeStorageDocumentService extends StorageDocumentServiceImpl {

        private StorageDocumentDO savedEntity;

        private FakeStorageDocumentService(ObjectStorageClient objectStorageClient,
                                           DocumentIndexingService documentIndexingService,
                                           MetaRetrievalProperties retrievalProperties,
                                           MetaDocumentTypeResolver documentTypeResolver) {
            super(objectStorageClient, documentIndexingService, retrievalProperties, documentTypeResolver);
        }

        @Override
        public boolean save(StorageDocumentDO entity) {
            this.savedEntity = entity;
            return true;
        }

        @Override
        public boolean updateById(StorageDocumentDO entity) {
            this.savedEntity = entity;
            return true;
        }

        @Override
        public StorageDocumentDO getOne(com.baomidou.mybatisplus.core.conditions.Wrapper<StorageDocumentDO>
                                                queryWrapper) {
            return savedEntity;
        }

    }
}
