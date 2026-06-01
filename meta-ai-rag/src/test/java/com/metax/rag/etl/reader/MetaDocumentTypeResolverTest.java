package com.metax.rag.etl.reader;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MetaDocumentTypeResolverTest .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
class MetaDocumentTypeResolverTest {

    private final MetaDocumentTypeResolver resolver = new MetaDocumentTypeResolver();

    /**
     * 文件后缀应解析为标准文档类型
     */
    @Test
    void shouldResolveTypeByExtension() {
        assertThat(resolver.resolve(null, "demo.md")).isEqualTo("markdown");
        assertThat(resolver.resolve(null, "demo.json")).isEqualTo("json");
        assertThat(resolver.resolve(null, "demo.pdf")).isEqualTo("pdf");
        assertThat(resolver.resolve(null, "demo.unknown")).isEqualTo("tika");
    }

    /**
     * 显式类型应优先于文件后缀
     */
    @Test
    void shouldPreferExplicitType() {
        assertThat(resolver.resolve("json", "demo.md")).isEqualTo("json");
        assertThat(resolver.resolve(".md", "demo.json")).isEqualTo("markdown");
    }
}

