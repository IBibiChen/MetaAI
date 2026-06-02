package com.metax.rag.etl.reader;

import com.metax.rag.etl.resource.MetaDocumentResource;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;

import java.util.List;

/**
 * MetaDocumentReader .
 *
 * <p>
 * MetaAI 知识库文档 Reader，严格实现 Spring AI DocumentReader 接口
 * 它是项目统一 Reader 门面，不直接解析文件内容，也不负责创建具体官方 Reader
 *
 * <p>
 * 具体 JsonReader、TextReader、MarkdownDocumentReader、TikaDocumentReader 的创建参数由策略 Bean 管理
 * 当前类只持有请求级 Resource 和已选择的 delegate Reader
 *
 * <p>
 * 当前类是 Delegation(委托模式) 的落点
 * MetaDocumentReader.get 不做格式判断，也不解析文件内容
 * 它只把 Spring AI DocumentReader 标准调用委托给 delegate Reader
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
public class MetaDocumentReader implements DocumentReader {

    private final MetaDocumentResource documentResource;

    private final DocumentReader delegate;

    public MetaDocumentReader(MetaDocumentResource documentResource, DocumentReader delegate) {
        this.documentResource = documentResource;
        this.delegate = delegate;
    }

    /**
     * 读取并解析文档
     *
     * <p>
     * 该方法是 Spring AI DocumentReader 的标准入口
     * 当前类只负责统一入口和委托执行，具体解析交给策略 Bean 创建出的官方 Reader
     *
     * @return Document 列表
     */
    @Override
    public List<Document> get() {
        try {
            return delegate.read();
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to read document: type = %s, source = %s"
                    .formatted(documentResource.documentType(), documentResource.source()), ex);
        }
    }
}

