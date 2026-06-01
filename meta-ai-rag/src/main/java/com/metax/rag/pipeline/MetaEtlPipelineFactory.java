package com.metax.rag.pipeline;

import com.metax.rag.core.VectorStoreRouter;
import com.metax.rag.etl.reader.MetaDocumentReaderFactory;
import com.metax.rag.etl.transformer.MetaDocumentTransformerFactory;
import com.metax.rag.etl.writer.MetaDocumentSnapshotWriter;
import com.metax.rag.indexing.DocumentIndexingContext;
import com.metax.rag.indexing.DocumentIndexingRequest;
import com.metax.rag.model.MetadataKeys;
import com.metax.rag.config.RagProperties;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.document.DocumentWriter;
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
 * snapshot 开启时会额外装配 FileDocumentWriter 快照写入，不影响 VectorStore 作为最终写入端
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

    private final RagProperties properties;

    private final VectorStoreRouter vectorStoreRouter;

    private final MetaDocumentReaderFactory documentReaderFactory;

    private final MetaDocumentTransformerFactory documentTransformerFactory;

    public MetaEtlPipelineFactory(RagProperties properties,
                                  VectorStoreRouter vectorStoreRouter,
                                  MetaDocumentReaderFactory documentReaderFactory,
                                  MetaDocumentTransformerFactory documentTransformerFactory) {
        this.properties = properties;
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
        // provider + vectorStore 决定写入哪个向量空间，查询时必须使用同一组选择
        VectorStore vectorStore = vectorStoreRouter.getVectorStore(request.provider(), request.vectorStore());
        // Reader 阶段把对象存储或本地文件解析为 Spring AI Document
        DocumentReader reader = documentReaderFactory.create(context.documentResource());
        // Transformer 顺序是 ETL 语义约束：先补文档级 metadata，再切分 chunk，再补 chunk 级 metadata，最后设置内容格式化规则
        // Transformer 顺序固定为文档 metadata -> chunk 切分 -> chunk metadata -> 内容格式化
        List<DocumentTransformer> transformers = List.of(
                documentTransformerFactory.documentMetadataEnricher(request),
                documentTransformerFactory.tokenTextSplitter(),
                documentTransformerFactory.chunkMetadataEnricher(request),
                documentTransformerFactory.contentFormatTransformer()
        );
        // snapshot writer 用于导出 ETL 快照，排查实际写入向量库前的 chunk 内容
        List<DocumentWriter> snapshotWriters = snapshotWriters(request);
        // Sink 收敛 delete + write 策略，最终写入仍委托 Spring AI VectorStore
        MetaVectorStoreSink sink = new MetaVectorStoreSink(vectorStore, documentFilter(request));
        return new MetaEtlUpsertPipeline(request, reader, transformers, snapshotWriters, sink);
    }

    /**
     * 创建 ETL 快照 Writer 列表
     *
     * <p>
     * snapshot 关闭时返回空列表，pipeline 会直接跳过快照写入
     * snapshot 开启时创建 MetaDocumentSnapshotWriter，用 Spring AI FileDocumentWriter 导出 transform 后的 Document
     *
     * <p>
     * 这里保留 List<DocumentWriter> 而不是单个 Writer，是为了继续使用 Spring AI DocumentWriter 抽象
     * 后续如果需要同时输出本地文件、对象存储快照或审计样本，可以在这里追加多个 Writer
     *
     * @param request 文档索引请求
     * @return ETL 快照 Writer 列表
     */
    private List<DocumentWriter> snapshotWriters(DocumentIndexingRequest request) {
        if (!properties.getSnapshot().isEnabled()) {
            return List.of();
        }
        return List.of(new MetaDocumentSnapshotWriter(request, properties.getSnapshot()));
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
