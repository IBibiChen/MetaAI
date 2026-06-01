package com.metax.rag.etl.reader;

import com.metax.rag.etl.resource.MetaDocumentResource;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.TextReader;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * TextDocumentReaderStrategy .
 *
 * <p>
 * 纯文本文档 Reader 策略，委托 Spring AI TextReader 解析文本资源
 * 文本读取固定使用 UTF-8，避免操作系统默认字符集影响中文内容
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
@Component
public class TextDocumentReaderStrategy implements MetaDocumentReaderStrategy {

    /**
     * 支持 txt 文档类型
     *
     * @param documentType 文档类型
     * @return 是否支持
     */
    @Override
    public boolean supports(String documentType) {
        return "txt".equals(documentType);
    }

    /**
     * 创建 TextReader
     *
     * @param documentResource 文档资源
     * @return DocumentReader
     */
    @Override
    public DocumentReader create(MetaDocumentResource documentResource) {
        TextReader reader = new TextReader(documentResource.resource());
        reader.setCharset(StandardCharsets.UTF_8);
        reader.getCustomMetadata().put("source", documentResource.source());
        return reader;
    }
}
