package com.metax.external.adapter.sync.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ExternalDocumentLearningStatusTest .
 *
 * <p>
 * 第三方系统学习状态编码测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/11
 */
class ExternalDocumentLearningStatusTest {

    @Test
    void shouldKeepExternalStatusCode() {
        assertThat(ExternalDocumentLearningStatus.LEARNING.code()).isEqualTo(0);
        assertThat(ExternalDocumentLearningStatus.PUSH_FAILED.code()).isEqualTo(1);
        assertThat(ExternalDocumentLearningStatus.COMPLETED.code()).isEqualTo(2);
        assertThat(ExternalDocumentLearningStatus.FAILED.code()).isEqualTo(3);
    }
}


