package com.metax.external.adapter.sync;

import com.metax.external.adapter.config.ExternalAdapterProperties;
import com.metax.external.adapter.source.ExternalSourceFileDO;
import com.metax.external.adapter.source.ExternalSourceFileService;
import com.metax.external.adapter.web.ExternalDocumentSyncResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ExternalDocumentReconcilerTest .
 *
 * <p>
 * 第三方系统补偿扫描调度测试
 */
class ExternalDocumentReconcilerTest {

    @Test
    void reconcileFilesShouldSkipWhenDisabled() {
        ExternalAdapterProperties properties = properties(false);
        ExternalSourceFileService sourceFileService = mock(ExternalSourceFileService.class);
        ExternalDocumentSyncService syncService = mock(ExternalDocumentSyncService.class);
        ExternalDocumentReconciler reconciler = new ExternalDocumentReconciler(properties, sourceFileService,
                syncService);

        reconciler.reconcileFiles();

        verify(sourceFileService, never()).findLearnableForReconcile(20, 3);
        verify(syncService, never()).requestSync("file-1");
    }

    @Test
    void reconcileFilesShouldEnqueueCandidatesAndContinueAfterFailure() {
        ExternalAdapterProperties properties = properties(true);
        ExternalSourceFileService sourceFileService = mock(ExternalSourceFileService.class);
        ExternalDocumentSyncService syncService = mock(ExternalDocumentSyncService.class);
        ExternalSourceFileDO first = file("file-1");
        ExternalSourceFileDO second = file("file-2");
        when(sourceFileService.findLearnableForReconcile(20, 3)).thenReturn(List.of(first, second));
        when(syncService.requestSync("file-1")).thenThrow(new IllegalStateException("source missing"));
        when(syncService.requestSync("file-2")).thenReturn(new ExternalDocumentSyncResponse("file-2",
                "doc-2", "PENDING", 0));
        ExternalDocumentReconciler reconciler = new ExternalDocumentReconciler(properties, sourceFileService,
                syncService);

        reconciler.reconcileFiles();

        verify(sourceFileService).findLearnableForReconcile(20, 3);
        verify(syncService).requestSync("file-1");
        verify(syncService).requestSync("file-2");
    }

    @Test
    void reconcileIndexStatusShouldSkipWhenDisabled() {
        ExternalAdapterProperties properties = properties(false);
        ExternalSourceFileService sourceFileService = mock(ExternalSourceFileService.class);
        ExternalDocumentSyncService syncService = mock(ExternalDocumentSyncService.class);
        ExternalDocumentReconciler reconciler = new ExternalDocumentReconciler(properties, sourceFileService,
                syncService);

        reconciler.reconcileIndexStatus();

        verify(syncService, never()).reconcileIndexStatus(20);
    }

    @Test
    void reconcileIndexStatusShouldDelegateWhenEnabled() {
        ExternalAdapterProperties properties = properties(true);
        ExternalSourceFileService sourceFileService = mock(ExternalSourceFileService.class);
        ExternalDocumentSyncService syncService = mock(ExternalDocumentSyncService.class);
        ExternalDocumentReconciler reconciler = new ExternalDocumentReconciler(properties, sourceFileService,
                syncService);

        reconciler.reconcileIndexStatus();

        verify(syncService).reconcileIndexStatus(20);
    }

    private ExternalAdapterProperties properties(boolean reconcileEnabled) {
        ExternalAdapterProperties properties = new ExternalAdapterProperties();
        properties.setMaxAttempts(3);
        properties.getReconcile().setEnabled(reconcileEnabled);
        properties.getReconcile().setBatchSize(20);
        return properties;
    }

    private ExternalSourceFileDO file(String id) {
        ExternalSourceFileDO file = new ExternalSourceFileDO();
        file.setId(id);
        return file;
    }
}
