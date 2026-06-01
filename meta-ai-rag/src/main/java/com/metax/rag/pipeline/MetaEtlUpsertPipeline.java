package com.metax.rag.pipeline;

import com.metax.rag.indexing.DocumentIndexingRequest;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.document.DocumentTransformer;

import java.util.List;

/**
 * MetaEtlUpsertPipeline .
 *
 * <p>
 * RAG 文档 upsert 执行计划，保存一次文档索引所需的 Reader、Transformer 和 VectorStore Sink
 * 它对应 DashScope upsertPipeline 中先组装 pipeline request，再统一执行的设计思路
 *
 * @param request      文档索引请求
 * @param reader       DocumentReader
 * @param transformers Transformer 链路
 * @param sink         VectorStore 写入端
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/1
 */
public record MetaEtlUpsertPipeline(
        DocumentIndexingRequest request,
        DocumentReader reader,
        List<DocumentTransformer> transformers,
        MetaVectorStoreSink sink
) {

    /**
     * 执行 upsert 文档索引
     *
     * <p>
     * upsert 语义固定为先按 tenantId + knowledgeBaseId + documentId 删除旧 chunk，再写入新 chunk
     *
     * @return Pipeline 执行结果
     */
    public MetaEtlPipelineResult upsert() {
        // read 阶段只解析原始文件，不补业务 metadata，也不做切分
        List<Document> documents = reader.read();
        for (DocumentTransformer transformer : transformers) {
            // transform 阶段按工厂定义的顺序执行，前一个输出就是后一个输入
            documents = transformer.transform(documents);
        }
        // upsert 阶段先删除同 documentId 的旧 chunk，再写入本次生成的新 chunk
        sink.upsert(documents);
        return new MetaEtlPipelineResult(documents.size());
    }
}
