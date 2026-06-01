package com.metax.rag.etl.reader;

import com.metax.rag.etl.resource.MetaDocumentResource;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.stereotype.Component;

/**
 * TikaDocumentReaderStrategy .
 *
 * <p>
 * 复杂文档 Reader 策略，委托 Spring AI TikaDocumentReader 解析 PDF、DOCX、HTML 等复杂格式
 * 当 documentType 无明确策略时，本策略作为兜底解析器
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
@Component
public class TikaDocumentReaderStrategy implements MetaDocumentReaderStrategy {

    /**
     * 支持 tika 文档类型
     *
     * @param documentType 文档类型
     * @return 是否支持
     */
    @Override
    public boolean supports(String documentType) {
        return "tika".equals(documentType);
    }

    /**
     * 创建 TikaDocumentReader
     *
     * @param documentResource 文档资源
     * @return DocumentReader
     */
    @Override
    public DocumentReader create(MetaDocumentResource documentResource) {
        return new TikaDocumentReader(documentResource.resource());
    }
}
