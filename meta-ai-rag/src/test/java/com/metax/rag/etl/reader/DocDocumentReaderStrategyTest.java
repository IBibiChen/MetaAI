package com.metax.rag.etl.reader;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DocDocumentReaderStrategyTest .
 *
 * <p>
 * 旧版 Word .doc 文档 Reader 策略测试
 */
class DocDocumentReaderStrategyTest {

    /**
     * doc 类型应命中专用 Reader 策略
     */
    @Test
    void shouldSupportDoc() {
        DocDocumentReaderStrategy strategy = new DocDocumentReaderStrategy();

        assertThat(strategy.supports("doc")).isTrue();
    }

    /**
     * docx 类型不能被 doc 专用策略接管
     */
    @Test
    void shouldNotSupportDocx() {
        DocDocumentReaderStrategy strategy = new DocDocumentReaderStrategy();

        assertThat(strategy.supports("docx")).isFalse();
    }
}
