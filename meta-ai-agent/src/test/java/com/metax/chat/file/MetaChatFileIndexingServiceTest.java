package com.metax.chat.file;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.metax.rag.config.MetaRetrievalProperties;
import com.metax.rag.etl.reader.MetaDocumentReaderFactory;
import com.metax.rag.etl.transformer.MetaDocumentTransformerFactory;
import com.metax.rag.model.MetadataKeys;
import com.metax.rag.pipeline.MetaVectorStoreWriter;
import com.metax.rag.storage.ObjectStorageClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.util.DigestUtils.md5DigestAsHex;

/**
 * MetaChatFileIndexingServiceTest .
 *
 * <p>
 * 会话文件临时索引服务单元测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/10
 */
class MetaChatFileIndexingServiceTest {

    /**
     * 索引服务应写入 session scope 文件 metadata
     */
    @Test
    void indexShouldWriteFileMetadataWithoutDocumentId() {
        ObjectStorageClient objectStorageClient = mock(ObjectStorageClient.class);
        MetaDocumentReaderFactory readerFactory = mock(MetaDocumentReaderFactory.class);
        MetaDocumentTransformerFactory transformerFactory = mock(MetaDocumentTransformerFactory.class);
        VectorStore vectorStore = mock(VectorStore.class);
        AtomicReference<List<Document>> writtenDocuments = new AtomicReference<>();
        when(readerFactory.create(any())).thenReturn(reader(List.of(new Document("文件内容"))));
        when(transformerFactory.tokenTextSplitter()).thenReturn(identityTransformer());
        when(transformerFactory.contentFormatTransformer()).thenReturn(identityTransformer());
        MetaVectorStoreWriter vectorStoreWriter = new MetaVectorStoreWriter(vectorStore, new MetaRetrievalProperties());
        MetaChatFileDO entity = readyFile();
        MetaChatFileIndexingService service = new TestMetaChatFileIndexingService(objectStorageClient,
                readerFactory, transformerFactory, vectorStoreWriter, entity);

        service.index("f1");

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
        assertThat(entity.getParseStatus()).isEqualTo(MetaChatFileStatus.READY.name());
        assertThat(entity.getChunkCount()).isEqualTo(1);
        assertThat(((TestMetaChatFileIndexingService) service).updatedStatuses())
                .containsExactly(MetaChatFileStatus.PARSING.name(), MetaChatFileStatus.READY.name());
    }

    /**
     * 构造会话文件元数据
     *
     * @return 会话文件元数据
     */
    private MetaChatFileDO readyFile() {
        MetaChatFileDO entity = new MetaChatFileDO();
        entity.setId(1L);
        entity.setFileId("f1");
        entity.setTenantId("t1");
        entity.setUserId("u1");
        entity.setChatId("t1-u1-s1");
        entity.setOriginalFilename("demo.pdf");
        entity.setDocumentType("pdf");
        entity.setBucket("meta-ai");
        entity.setObjectKey("chat-files/t1/u1/demo.pdf");
        entity.setContentType("application/pdf");
        entity.setFileSize(4L);
        entity.setFileSha256("sha256");
        entity.setParseStatus(MetaChatFileStatus.PARSING.name());
        entity.setChunkCount(0);
        entity.setDeleted(Boolean.FALSE);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    /**
     * 构造固定文档 Reader
     *
     * @param documents 文档列表
     * @return 文档 Reader
     */
    private DocumentReader reader(List<Document> documents) {
        return () -> documents;
    }

    /**
     * 构造不改变文档的 Transformer
     *
     * @return 文档 Transformer
     */
    private DocumentTransformer identityTransformer() {
        return documents -> documents;
    }

    private static final class TestMetaChatFileIndexingService extends MetaChatFileIndexingService {

        private final MetaChatFileDO file;

        private final List<String> updatedStatuses = new ArrayList<>();

        private TestMetaChatFileIndexingService(ObjectStorageClient objectStorageClient,
                                                MetaDocumentReaderFactory documentReaderFactory,
                                                MetaDocumentTransformerFactory documentTransformerFactory,
                                                MetaVectorStoreWriter vectorStoreWriter,
                                                MetaChatFileDO file) {
            super(objectStorageClient, documentReaderFactory, documentTransformerFactory, vectorStoreWriter);
            this.file = file;
        }

        @Override
        public MetaChatFileDO getOne(Wrapper<MetaChatFileDO> queryWrapper, boolean throwEx) {
            return file;
        }

        @Override
        public boolean updateById(MetaChatFileDO entity) {
            updatedStatuses.add(entity.getParseStatus());
            return true;
        }

        private List<String> updatedStatuses() {
            return updatedStatuses;
        }
    }
}
