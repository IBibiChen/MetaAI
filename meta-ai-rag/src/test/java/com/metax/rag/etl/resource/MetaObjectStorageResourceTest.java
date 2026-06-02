package com.metax.rag.etl.resource;

import com.metax.rag.storage.DocumentStorageService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MetaObjectStorageResourceTest .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/2
 */
class MetaObjectStorageResourceTest {

    /**
     * getInputStream 应委托 DocumentStorageService 读取对象流
     */
    @Test
    void shouldDelegateInputStreamToDocumentStorageService() throws Exception {
        DocumentStorageService storageService = mock(DocumentStorageService.class);
        when(storageService.getObject("bucket", "docs/demo.md"))
                .thenReturn(new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)));
        MetaObjectStorageResource resource = new MetaObjectStorageResource(storageService, "bucket", "docs/demo.md");

        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(content).isEqualTo("content");
        verify(storageService).getObject("bucket", "docs/demo.md");
    }
}
