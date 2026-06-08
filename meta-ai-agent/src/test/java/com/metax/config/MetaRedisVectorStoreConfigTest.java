package com.metax.config;

import com.metax.rag.config.RagProperties;
import com.metax.rag.model.MetadataKeys;
import com.metax.rag.retrieval.filter.RetrievalFilterExpressionFactory;
import com.metax.rag.retrieval.model.RetrievalOptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.redis.RedisFilterExpressionConverter;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MetaRedisVectorStoreConfigTest .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
class MetaRedisVectorStoreConfigTest {

    /**
     * Redis filter 转换器应接受项目 metadata 字段
     */
    @Test
    void shouldAllowProjectMetadataFieldsInRedisFilter() {
        MetaRedisVectorStoreConfig config = new MetaRedisVectorStoreConfig();
        RedisFilterExpressionConverter converter = new RedisFilterExpressionConverter(
                Arrays.asList(config.metadataFields()));

        String filter = converter.convertExpression(new RetrievalFilterExpressionFactory(new RagProperties()).create(
                RetrievalOptions.builder()
                        .tenantId("t1")
                        .kbId("kb1")
                        .documentId("doc1")
                        .documentType("doc")
                        .topK(5)
                        .similarityThreshold(0.5)
                        .build()));

        assertThat(filter)
                .contains("@scope:")
                .contains("@tenantId:")
                .contains("@kbId:")
                .contains("@documentId:")
                .contains("@documentType:");
        assertThat(Arrays.stream(config.metadataFields()).map(field -> field.name()).toList())
                .contains(MetadataKeys.DOCUMENT_NAME)
                .contains(MetadataKeys.FILE_NAME)
                .contains(MetadataKeys.VISIBILITY)
                .contains(MetadataKeys.DEPT_ID)
                .contains(MetadataKeys.USER_ID)
                .contains(MetadataKeys.SCOPE)
                .contains(MetadataKeys.CHAT_ID)
                .contains(MetadataKeys.FILE_ID);
    }
}
