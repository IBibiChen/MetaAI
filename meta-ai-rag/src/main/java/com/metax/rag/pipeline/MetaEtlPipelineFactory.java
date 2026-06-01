package com.metax.rag.pipeline;

import com.metax.rag.core.VectorStoreRouter;
import com.metax.rag.etl.reader.MetaDocumentReaderFactory;
import com.metax.rag.etl.transformer.MetaDocumentTransformerFactory;
import com.metax.rag.indexing.DocumentIndexingContext;
import com.metax.rag.indexing.DocumentIndexingRequest;
import com.metax.rag.model.MetadataKeys;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MetaEtlPipelineFactory .
 *
 * <p>
 * RAG ETL Pipeline 工厂，负责把文档索引请求组装成可执行的 upsert pipeline
 * 这里集中完成 dataSource、transformations、dataSink 和 upsert policy 的装配
 *
 * <p>
 * DashScope upsertPipeline 会先组装 embedding、parser、retriever、dataSource、dataSink 配置再执行
 * 本地 ETL 对应为先组装 Reader、Transformer 链路、VectorStore Writer 和 delete filter 再执行
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/1
 */
@Component
public class MetaEtlPipelineFactory {

    private final VectorStoreRouter vectorStoreRouter;

    private final MetaDocumentReaderFactory documentReaderFactory;

    private final MetaDocumentTransformerFactory documentTransformerFactory;

    public MetaEtlPipelineFactory(VectorStoreRouter vectorStoreRouter,
                                  MetaDocumentReaderFactory documentReaderFactory,
                                  MetaDocumentTransformerFactory documentTransformerFactory) {
        this.vectorStoreRouter = vectorStoreRouter;
        this.documentReaderFactory = documentReaderFactory;
        this.documentTransformerFactory = documentTransformerFactory;
    }

    /**
     * 创建 upsert pipeline
     *
     * @param context 文档索引上下文
     * @return upsert pipeline
     */
    public MetaEtlUpsertPipeline createUpsertPipeline(DocumentIndexingContext context) {
        DocumentIndexingRequest request = context.request();
        VectorStore vectorStore = vectorStoreRouter.getVectorStore(request.provider(), request.vectorStore());
        DocumentReader reader = documentReaderFactory.create(context.documentResource());
        List<DocumentTransformer> transformers = List.of(
                documentTransformerFactory.documentMetadata(request),
                documentTransformerFactory.splitter(),
                documentTransformerFactory.chunkMetadata(request),
                documentTransformerFactory.contentFormat()
        );
        MetaVectorStoreSink sink = new MetaVectorStoreSink(vectorStore, documentFilter(request));
        return new MetaEtlUpsertPipeline(request, reader, transformers, sink);
    }

    private org.springframework.ai.vectorstore.filter.Filter.Expression documentFilter(DocumentIndexingRequest request) {
        // 删除旧 chunk 时只使用稳定业务字段，不使用 chunkId，因为这里要覆盖整个文档的全部 chunk
        // 这个过滤表达式依赖写入 metadata 与 Redis / Qdrant / Milvus 过滤字段保持同名
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        return builder.and(
                builder.and(builder.eq(MetadataKeys.TENANT_ID, request.tenantId()),
                        builder.eq(MetadataKeys.KNOWLEDGE_BASE_ID, request.knowledgeBaseId())),
                builder.eq(MetadataKeys.DOCUMENT_ID, request.documentId())
        ).build();
    }
}
