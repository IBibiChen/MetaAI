package com.metax.rag.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MetadataKeysTest .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
class MetadataKeysTest {

    /**
     * metadata key 应保持项目统一命名
     */
    @Test
    void shouldUseCamelCaseMetadataKeys() {
        assertThat(MetadataKeys.TENANT_ID).isEqualTo("tenantId");
        assertThat(MetadataKeys.KB_ID).isEqualTo("kbId");
        assertThat(MetadataKeys.DOCUMENT_ID).isEqualTo("documentId");
        assertThat(MetadataKeys.DOCUMENT_TYPE).isEqualTo("documentType");
        assertThat(MetadataKeys.CREATED_AT).isEqualTo("createdAt");
        assertThat(MetadataKeys.CHUNK_ID).isEqualTo("chunkId");
        assertThat(MetadataKeys.CHUNK_INDEX).isEqualTo("chunkIndex");
        assertThat(MetadataKeys.CONTENT_HASH).isEqualTo("contentHash");
    }
}
