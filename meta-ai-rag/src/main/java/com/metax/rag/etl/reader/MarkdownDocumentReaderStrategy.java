package com.metax.rag.etl.reader;

import com.metax.rag.etl.resource.MetaDocumentResource;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.stereotype.Component;

/**
 * MarkdownDocumentReaderStrategy .
 *
 * <p>
 * Markdown 文档 Reader 策略，委托 Spring AI MarkdownDocumentReader 解析 markdown 结构
 * 标题会进入 metadata，正文段落会进入 Document text
 *
 * <p>
 * Spring AI 1.1.7 MarkdownDocumentReader 使用 JVM 默认字符集读取 Resource，不暴露 charset 配置
 * 生产环境建议通过 JVM 参数固定 -Dfile.encoding=UTF-8，避免 Windows 默认字符集导致中文 markdown 乱码
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
@Component
public class MarkdownDocumentReaderStrategy implements MetaDocumentReaderStrategy {

    /**
     * 支持 markdown 文档类型
     *
     * @param documentType 文档类型
     * @return 是否支持
     */
    @Override
    public boolean supports(String documentType) {
        return "markdown".equals(documentType);
    }

    /**
     * 创建 MarkdownDocumentReader
     *
     * @param documentResource 文档资源
     * @return DocumentReader
     */
    @Override
    public DocumentReader create(MetaDocumentResource documentResource) {
        return new MarkdownDocumentReader(documentResource.resource(), MarkdownDocumentReaderConfig.defaultConfig());
    }
}
