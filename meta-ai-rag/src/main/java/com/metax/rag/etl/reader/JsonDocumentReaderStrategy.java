package com.metax.rag.etl.reader;

import com.metax.rag.etl.resource.MetaDocumentResource;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.JsonReader;
import org.springframework.stereotype.Component;

/**
 * JsonDocumentReaderStrategy .
 *
 * <p>
 * JSON 文档 Reader 策略，委托 Spring AI JsonReader 解析 JSON 资源
 * 当前使用 JsonReader 默认字段策略，后续如需限定字段可在本策略中集中调整
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
@Component
public class JsonDocumentReaderStrategy implements MetaDocumentReaderStrategy {

    /**
     * 支持 json 文档类型
     *
     * @param documentType 文档类型
     * @return 是否支持
     */
    @Override
    public boolean supports(String documentType) {
        return "json".equals(documentType);
    }

    /**
     * 创建 JsonReader
     *
     * @param documentResource 文档资源
     * @return DocumentReader
     */
    @Override
    public DocumentReader create(MetaDocumentResource documentResource) {
        return new JsonReader(documentResource.resource());
    }
}
