package com.metax.rag.etl.reader;

import com.metax.rag.etl.resource.MetaDocumentResource;
import org.springframework.ai.document.DocumentReader;
import org.springframework.stereotype.Component;

/**
 * DocDocumentReaderStrategy .
 *
 * <p>
 * 旧版 Word .doc 文档 Reader 策略，使用 LibreOffice headless 先转换为 docx 后再解析
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/23
 */
@Component
public class DocDocumentReaderStrategy implements MetaDocumentReaderStrategy {

    /**
     * 支持旧版 Word .doc 文档类型
     *
     * @param documentType 文档类型
     * @return 是否支持
     */
    @Override
    public boolean supports(String documentType) {
        return "doc".equals(documentType);
    }

    /**
     * 创建 DocDocumentReader
     *
     * @param documentResource 文档资源
     * @return DocumentReader
     */
    @Override
    public DocumentReader create(MetaDocumentResource documentResource) {
        return new DocDocumentReader(documentResource);
    }
}
