package com.metax.chat.file;

import com.metax.rag.etl.reader.MetaDocumentReaderFactory;
import com.metax.rag.etl.resource.MetaDocumentTypeResolver;
import com.metax.rag.etl.transformer.MetaDocumentTransformerFactory;
import com.metax.rag.config.MetaRetrievalProperties;
import com.metax.rag.model.MetadataKeys;
import com.metax.rag.pipeline.MetaVectorStoreWriter;
import com.metax.rag.storage.ObjectStorageClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.util.DigestUtils.md5DigestAsHex;

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
    void uploadAndIndexShouldWriteFileMetadataWithoutDocumentId() {
        ObjectStorageClient objectStorageClient = mock(ObjectStorageClient.class);
        MetaDocumentReaderFactory readerFactory = mock(MetaDocumentReaderFactory.class);
        MetaDocumentTransformerFactory transformerFactory = mock(MetaDocumentTransformerFactory.class);
        VectorStore vectorStore = mock(VectorStore.class);
        AtomicReference<List<Document>> writtenDocuments = new AtomicReference<>();
        when(objectStorageClient.defaultBucket()).thenReturn("meta-ai");
        when(objectStorageClient.putObject(eq("meta-ai"), any(), any(InputStream.class), eq(4L), eq("application/pdf")))
                .thenReturn(null);
        when(readerFactory.create(any())).thenReturn(reader(List.of(new Document("文件内容"))));
        when(transformerFactory.tokenTextSplitter()).thenReturn(identityTransformer());
        when(transformerFactory.contentFormatTransformer()).thenReturn(identityTransformer());
        MetaVectorStoreWriter vectorStoreWriter = new MetaVectorStoreWriter(vectorStore, new MetaRetrievalProperties());
        MetaChatFileServiceImpl service = new TestMetaChatFileServiceImpl(objectStorageClient,
                new MetaDocumentTypeResolver(), readerFactory, transformerFactory, vectorStore, vectorStoreWriter);
        MockMultipartFile file = new MockMultipartFile("files", "demo.pdf", "application/pdf",
                "test".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        service.uploadAndIndex("t1", "u1", "t1-u1-s1", new MockMultipartFile[]{file});

        verify(vectorStore).write(org.mockito.ArgumentMatchers.argThat(documents -> {
            writtenDocuments.set(documents);
            return true;
        }));
        verify(vectorStore).delete(org.mockito.ArgumentMatchers.<Filter.Expression>argThat(expression -> {
            String filter = expression.toString();
            return filter.contains("scope")
                    && filter.contains("session")
                    && filter.contains("tenantId")
                    && filter.contains("userId")
                    && filter.contains("chatId")
                    && filter.contains("fileId");
        }));
        Document document = writtenDocuments.get().get(0);
        assertThat(document.getMetadata())
                .containsEntry(MetadataKeys.SCOPE, MetadataKeys.SCOPE_SESSION)
                .containsEntry(MetadataKeys.TENANT_ID, "t1")
                .containsEntry(MetadataKeys.USER_ID, "u1")
                .containsEntry(MetadataKeys.CHAT_ID, "t1-u1-s1")
                .containsEntry(MetadataKeys.FILE_NAME, "demo.pdf")
                .containsEntry(MetadataKeys.CONTENT_HASH,
                        md5DigestAsHex("文件内容".getBytes(StandardCharsets.UTF_8)))
                .containsKey(MetadataKeys.FILE_ID)
                .doesNotContainKey(MetadataKeys.DOCUMENT_ID);
    }

    private DocumentReader reader(List<Document> documents) {
        return () -> documents;
    }

    private DocumentTransformer identityTransformer() {
        return documents -> documents;
    }

    private static final class TestMetaChatFileServiceImpl extends MetaChatFileServiceImpl {

        private TestMetaChatFileServiceImpl(ObjectStorageClient objectStorageClient,
                                            MetaDocumentTypeResolver documentTypeResolver,
                                            MetaDocumentReaderFactory documentReaderFactory,
                                            MetaDocumentTransformerFactory documentTransformerFactory,
                                            VectorStore vectorStore,
                                            MetaVectorStoreWriter vectorStoreWriter) {
            super(objectStorageClient, documentTypeResolver, documentReaderFactory, documentTransformerFactory,
                    vectorStore, vectorStoreWriter);
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
