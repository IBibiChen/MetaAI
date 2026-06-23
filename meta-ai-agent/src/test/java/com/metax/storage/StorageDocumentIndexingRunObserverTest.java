package com.metax.storage;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.metax.rag.indexing.DocumentIndexingRequest;
import com.metax.rag.indexing.DocumentIndexingRun;
import com.metax.rag.indexing.DocumentIndexingStatus;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * StorageDocumentIndexingRunObserverTest .
 *
 * <p>
 * 对象存储文档索引执行观察者单元测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
@ExtendWith(MockitoExtension.class)
class StorageDocumentIndexingRunObserverTest {

    @Mock
    private StorageDocumentMapper storageDocumentMapper;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(StorageDocumentMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, StorageDocumentDO.class);
    }

    /**
     * 索引成功时应回写索引状态和 chunk 数
     */
    @Test
    void shouldUpdateChunkCountWhenIndexSucceeded() {
        when(storageDocumentMapper.update(isNull(), any())).thenReturn(1);
        StorageDocumentIndexingRunObserver observer = new StorageDocumentIndexingRunObserver(storageDocumentMapper);

        observer.onRunChanged(run(DocumentIndexingStatus.SUCCEEDED, 31));

        Wrapper<StorageDocumentDO> wrapper = updatedWrapper();
        assertThat(wrapper.getSqlSet())
                .contains("index_status=")
                .contains("chunk_count=");
        assertThat(paramNameValuePairs(wrapper).values())
                .contains(StorageDocumentIndexStatus.INDEXED.name(), 31);
    }

    /**
     * 索引失败时不应清空上一次成功的 chunk 数
     */
    @Test
    void shouldKeepChunkCountWhenIndexFailed() {
        when(storageDocumentMapper.update(isNull(), any())).thenReturn(1);
        StorageDocumentIndexingRunObserver observer = new StorageDocumentIndexingRunObserver(storageDocumentMapper);

        observer.onRunChanged(run(DocumentIndexingStatus.FAILED, 0));

        Wrapper<StorageDocumentDO> wrapper = updatedWrapper();
        assertThat(wrapper.getSqlSet())
                .contains("index_status=")
                .doesNotContain("chunk_count=");
        assertThat(paramNameValuePairs(wrapper).values())
                .contains(StorageDocumentIndexStatus.INDEX_FAILED.name())
                .doesNotContain(0);
    }

    /**
     * 索引状态回写未命中文档时不应影响异步索引线程
     */
    @Test
    void shouldNotThrowWhenUpdateMatchedNoRows() {
        when(storageDocumentMapper.update(isNull(), any())).thenReturn(0);
        StorageDocumentIndexingRunObserver observer = new StorageDocumentIndexingRunObserver(storageDocumentMapper);

        observer.onRunChanged(run(DocumentIndexingStatus.FAILED, 0));

        Wrapper<StorageDocumentDO> wrapper = updatedWrapper();
        assertThat(wrapper.getSqlSet()).contains("index_status=");
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> paramNameValuePairs(Wrapper<StorageDocumentDO> wrapper) {
        return ((AbstractWrapper<StorageDocumentDO, ?, ?>) wrapper).getParamNameValuePairs();
    }

    @SuppressWarnings("unchecked")
    private Wrapper<StorageDocumentDO> updatedWrapper() {
        ArgumentCaptor<Wrapper<StorageDocumentDO>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(storageDocumentMapper).update(isNull(), wrapperCaptor.capture());
        return wrapperCaptor.getValue();
    }

    private DocumentIndexingRun run(DocumentIndexingStatus status, int chunkCount) {
        DocumentIndexingRequest request = DocumentIndexingRequest.builder()
                .tenantId("t1")
                .kbId("kb1")
                .documentId("doc-1")
                .visibility("PUBLIC")
                .documentType("txt")
                .source("source.txt")
                .documentName("source.txt")
                .bucket("bucket")
                .objectKey("object")
                .build();
        return DocumentIndexingRun.pending(request)
                .withStatus(status, chunkCount, "message");
    }
}
