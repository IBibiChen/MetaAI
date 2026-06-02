package com.metax.rag.storage;

import com.metax.rag.config.RagProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LegacyDocumentStorageServiceTest .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/2
 */
class LegacyDocumentStorageServiceTest {

    /**
     * 占位实现应明确提示老系统适配器未实现
     */
    @Test
    void shouldRejectGetObjectBeforeLegacyAdapterImplemented() {
        LegacyDocumentStorageService storageService = new LegacyDocumentStorageService(new RagProperties());

        assertThatThrownBy(() -> storageService.getObject("bucket", "objectKey"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("not implemented");
    }
}
