package com.metax.external.adapter.sync;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.metax.external.adapter.config.ExternalAdapterProperties;
import com.metax.external.adapter.source.ExternalFileDownloadClient;
import com.metax.external.adapter.source.ExternalSourceFileDO;
import com.metax.external.adapter.source.ExternalSourceFileService;
import com.metax.external.adapter.storage.ExternalStorageDocumentClient;
import com.metax.external.adapter.storage.ExternalStorageDocumentMapper;
import com.metax.external.adapter.sync.model.ExternalDocumentLearningStatus;
import com.metax.external.adapter.sync.model.ExternalDocumentSyncStatus;
import com.metax.external.adapter.web.ExternalDocumentSyncResponse;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ExternalDocumentSyncServiceTest .
 *
 * <p>
 * 第三方系统文件同步编排服务测试
 */
class ExternalDocumentSyncServiceTest {

    /**
     * 初始化 MyBatis Plus 表信息缓存
     *
     * <p>
     * 纯单元测试没有启动 MyBatis Mapper 扫描，LambdaUpdateWrapper 需要该缓存解析字段名
     */
    @BeforeAll
    static void initMyBatisPlusTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""),
                ExternalDocumentSyncDO.class);
    }

    /**
     * 失败记录 hash 未变化时应复用既有 documentId，避免补偿重试重复写入 RustFS
     */
    @Test
    void requestSyncShouldReuseDocumentWhenFailedHashUnchanged() {
        ExternalSourceFileService sourceFileService = mock(ExternalSourceFileService.class);
        when(sourceFileService.findLearnableById("file-1")).thenReturn(Optional.of(file("file-1", "hash-1")));
        ExternalDocumentSyncDO existing = sync("file-1", ExternalDocumentSyncStatus.FAILED, "doc-1", "hash-1");
        TestExternalDocumentSyncService service = service(sourceFileService, existing);

        ExternalDocumentSyncResponse response = service.requestSync("file-1");

        assertThat(response.documentId()).isEqualTo("doc-1");
        assertThat(response.syncStatus()).isEqualTo(ExternalDocumentSyncStatus.PENDING.name());
        assertThat(service.lastSqlSet()).doesNotContain("documentId");
        verify(sourceFileService).updateLearningStatus("file-1", ExternalDocumentLearningStatus.LEARNING, "doc-1");
    }

    /**
     * 失败记录 hash 变化时必须清空 documentId，下一轮重新下载并上传新文件内容
     */
    @Test
    void requestSyncShouldClearDocumentWhenFailedHashChanged() {
        ExternalSourceFileService sourceFileService = mock(ExternalSourceFileService.class);
        when(sourceFileService.findLearnableById("file-1")).thenReturn(Optional.of(file("file-1", "hash-2")));
        ExternalDocumentSyncDO existing = sync("file-1", ExternalDocumentSyncStatus.FAILED, "doc-1", "hash-1");
        TestExternalDocumentSyncService service = service(sourceFileService, existing);

        ExternalDocumentSyncResponse response = service.requestSync("file-1");

        assertThat(response.documentId()).isNull();
        assertThat(response.syncStatus()).isEqualTo(ExternalDocumentSyncStatus.PENDING.name());
        assertThat(service.lastSqlSet()).contains("documentId");
        verify(sourceFileService).updateLearningStatus("file-1", ExternalDocumentLearningStatus.LEARNING, null);
    }

    /**
     * 旧记录缺少 hash 但已有 documentId 时不能假定文件变化，应补齐快照并复用对象存储文档
     */
    @Test
    void requestSyncShouldReuseDocumentWhenExistingHashMissing() {
        ExternalSourceFileService sourceFileService = mock(ExternalSourceFileService.class);
        when(sourceFileService.findLearnableById("file-1")).thenReturn(Optional.of(file("file-1", "hash-1")));
        ExternalDocumentSyncDO existing = sync("file-1", ExternalDocumentSyncStatus.RETRY_WAIT, "doc-1", null);
        TestExternalDocumentSyncService service = service(sourceFileService, existing);

        ExternalDocumentSyncResponse response = service.requestSync("file-1");

        assertThat(response.documentId()).isEqualTo("doc-1");
        assertThat(response.syncStatus()).isEqualTo(ExternalDocumentSyncStatus.PENDING.name());
        assertThat(service.lastSqlSet()).doesNotContain("documentId");
    }

    /**
     * 已完成且 hash 未变化时保持幂等，不重新入队也不修改 documentId
     */
    @Test
    void requestSyncShouldKeepIndexedWhenHashUnchanged() {
        ExternalSourceFileService sourceFileService = mock(ExternalSourceFileService.class);
        when(sourceFileService.findLearnableById("file-1")).thenReturn(Optional.of(file("file-1", "hash-1")));
        ExternalDocumentSyncDO existing = sync("file-1", ExternalDocumentSyncStatus.INDEXED, "doc-1", "hash-1");
        TestExternalDocumentSyncService service = service(sourceFileService, existing);

        ExternalDocumentSyncResponse response = service.requestSync("file-1");

        assertThat(response.documentId()).isEqualTo("doc-1");
        assertThat(response.syncStatus()).isEqualTo(ExternalDocumentSyncStatus.INDEXED.name());
        assertThat(service.lastSqlSet()).doesNotContain("documentId", "syncStatus");
    }

    /**
     * 已完成记录缺少旧 hash 时只能补齐源文件快照，不能把历史数据误判为文件变化
     */
    @Test
    void requestSyncShouldKeepIndexedWhenExistingHashMissing() {
        ExternalSourceFileService sourceFileService = mock(ExternalSourceFileService.class);
        when(sourceFileService.findLearnableById("file-1")).thenReturn(Optional.of(file("file-1", "hash-1")));
        ExternalDocumentSyncDO existing = sync("file-1", ExternalDocumentSyncStatus.INDEXED, "doc-1", null);
        TestExternalDocumentSyncService service = service(sourceFileService, existing);

        ExternalDocumentSyncResponse response = service.requestSync("file-1");

        assertThat(response.documentId()).isEqualTo("doc-1");
        assertThat(response.syncStatus()).isEqualTo(ExternalDocumentSyncStatus.INDEXED.name());
        assertThat(service.lastSqlSet()).doesNotContain("documentId", "syncStatus");
    }

    /**
     * 已完成但 hash 变化时需要重新上传新文件，旧 documentId 不能继续复用
     */
    @Test
    void requestSyncShouldClearIndexedDocumentWhenHashChanged() {
        ExternalSourceFileService sourceFileService = mock(ExternalSourceFileService.class);
        when(sourceFileService.findLearnableById("file-1")).thenReturn(Optional.of(file("file-1", "hash-2")));
        ExternalDocumentSyncDO existing = sync("file-1", ExternalDocumentSyncStatus.INDEXED, "doc-1", "hash-1");
        TestExternalDocumentSyncService service = service(sourceFileService, existing);

        ExternalDocumentSyncResponse response = service.requestSync("file-1");

        assertThat(response.documentId()).isNull();
        assertThat(response.syncStatus()).isEqualTo(ExternalDocumentSyncStatus.PENDING.name());
        assertThat(service.lastSqlSet()).contains("documentId");
    }

    private TestExternalDocumentSyncService service(ExternalSourceFileService sourceFileService,
                                                    ExternalDocumentSyncDO existing) {
        ExternalAdapterProperties properties = new ExternalAdapterProperties();
        properties.setTenantId("t1");
        properties.setKbId("kb1");
        properties.setMaxAttempts(3);
        return new TestExternalDocumentSyncService(sourceFileService, properties, existing);
    }

    private ExternalSourceFileDO file(String id, String hashCode) {
        ExternalSourceFileDO file = new ExternalSourceFileDO();
        file.setId(id);
        file.setFilePath("disk/demo.docx");
        file.setHashCode(hashCode);
        return file;
    }

    private ExternalDocumentSyncDO sync(String externalFileId,
                                        ExternalDocumentSyncStatus status,
                                        String documentId,
                                        String hashCode) {
        return ExternalDocumentSyncDO.builder()
                .id(1L)
                .externalFileId(externalFileId)
                .externalFilePath("disk/demo.docx")
                .externalHashCode(hashCode)
                .tenantId("t1")
                .kbId("kb1")
                .documentId(documentId)
                .syncStatus(status.name())
                .attemptCount(1)
                .build();
    }

    private static class TestExternalDocumentSyncService extends ExternalDocumentSyncService {

        private final ExternalDocumentSyncDO sync;

        private final List<String> sqlSets = new ArrayList<>();

        TestExternalDocumentSyncService(ExternalSourceFileService sourceFileService,
                                        ExternalAdapterProperties properties,
                                        ExternalDocumentSyncDO sync) {
            super(sourceFileService, mock(ExternalFileDownloadClient.class), mock(ExternalStorageDocumentClient.class),
                    properties, mock(ExternalStorageDocumentMapper.class));
            this.sync = sync;
        }

        @Override
        public ExternalDocumentSyncDO getOne(Wrapper<ExternalDocumentSyncDO> queryWrapper) {
            return sync;
        }

        @Override
        public boolean save(ExternalDocumentSyncDO entity) {
            throw new UnsupportedOperationException("当前测试只覆盖既有同步记录");
        }

        @Override
        public boolean update(Wrapper<ExternalDocumentSyncDO> updateWrapper) {
            if (updateWrapper instanceof LambdaUpdateWrapper<ExternalDocumentSyncDO> wrapper) {
                String sqlSet = wrapper.getSqlSet();
                sqlSets.add(sqlSet);
                if (sqlSet.contains("syncStatus")) {
                    sync.setSyncStatus(ExternalDocumentSyncStatus.PENDING.name());
                }
                if (sqlSet.contains("documentId")) {
                    sync.setDocumentId(null);
                }
            }
            return true;
        }

        String lastSqlSet() {
            return sqlSets.get(sqlSets.size() - 1);
        }
    }
}
