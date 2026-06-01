package com.metax.rag.etl.reader;

import com.metax.rag.etl.resource.MetaDocumentResource;
import org.springframework.ai.document.DocumentReader;

/**
 * MetaDocumentReaderStrategy .
 *
 * <p>
 * 文档 Reader 创建策略，负责根据请求级 Resource 创建 Spring AI 官方 DocumentReader
 * 策略 Bean 必须保持无状态，不能把 Resource 保存为 Spring 单例字段
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
public interface MetaDocumentReaderStrategy {

    /**
     * 是否支持指定文档类型
     *
     * @param documentType 文档类型
     * @return 是否支持
     */
    boolean supports(String documentType);

    /**
     * 创建请求级官方 Reader
     *
     * @param documentResource 文档资源
     * @return DocumentReader
     */
    DocumentReader create(MetaDocumentResource documentResource);
}
