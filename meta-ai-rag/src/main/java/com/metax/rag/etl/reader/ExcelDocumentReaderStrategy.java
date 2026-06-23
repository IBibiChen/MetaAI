package com.metax.rag.etl.reader;

import com.metax.rag.etl.resource.MetaDocumentResource;
import org.springframework.ai.document.DocumentReader;
import org.springframework.stereotype.Component;

/**
 * ExcelDocumentReaderStrategy .
 *
 * <p>
 * Excel 文档 Reader 策略，使用 Apache Fesod 流式读取 xls / xlsx 中真实有值的单元格
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/23
 */
@Component
public class ExcelDocumentReaderStrategy implements MetaDocumentReaderStrategy {

    /**
     * 支持 Excel 文档类型
     *
     * @param documentType 文档类型
     * @return 是否支持
     */
    @Override
    public boolean supports(String documentType) {
        return "xls".equals(documentType) || "xlsx".equals(documentType);
    }

    /**
     * 创建 ExcelDocumentReader
     *
     * @param documentResource 文档资源
     * @return DocumentReader
     */
    @Override
    public DocumentReader create(MetaDocumentResource documentResource) {
        return new ExcelDocumentReader(documentResource);
    }
}
