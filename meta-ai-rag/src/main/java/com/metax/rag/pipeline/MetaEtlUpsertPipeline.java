package com.metax.rag.pipeline;

import com.metax.rag.indexing.DocumentIndexingRequest;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.document.DocumentWriter;

import java.util.List;

/**
 * MetaEtlUpsertPipeline .
 *
 * <p>
 * RAG 文档覆盖式索引执行计划，保存一次文档索引所需的 Reader、Transformer 和 VectorStore Sink
 * 它对应 DashScope upsertPipeline 中先组装 pipeline request，再统一执行的设计思路
 * snapshotWriters 用于导出 ETL 快照，例如 FileDocumentWriter 快照，不改变生产索引写入端
 *
 * @param request         文档索引请求
 * @param reader          DocumentReader
 * @param transformers    Transformer 链路
 * @param snapshotWriters 快照 DocumentWriter，例如 ETL 快照导出
 * @param sink            VectorStore 写入端
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/1
 */
public record MetaEtlUpsertPipeline(
        DocumentIndexingRequest request,
        DocumentReader reader,
        List<DocumentTransformer> transformers,
        List<DocumentWriter> snapshotWriters,
        MetaVectorStoreSink sink
) {

    /**
     * Java record 紧凑构造器
     *
     * <p>
     * record 会自动接收 request、reader、transformers、snapshotWriters、sink 这些组件参数
     * 紧凑构造器不需要在括号里重复声明参数，方法体中的同名变量就是 record 组件入参
     * 调用 new MetaEtlUpsertPipeline(request, reader, transformers, snapshotWriters, sink) 时会进入这里
     * 它不是无参构造器，不能用 new MetaEtlUpsertPipeline() 调用
     *
     * <p>
     * record 头中的每一项叫 record component
     * 当前 record 有 5 个 component，因此主构造器默认也必须接收 5 个参数
     * 参数顺序必须和 record 头声明顺序一致，Java 调用构造器时不看变量名，只看参数位置和类型
     *
     * <p>
     * 正确调用示例
     * <pre>{@code
     * new MetaEtlUpsertPipeline(request, reader, transformers, snapshotWriters, sink)
     * }</pre>
     *
     * <p>
     * 错误调用示例
     * <pre>{@code
     * new MetaEtlUpsertPipeline(request, reader, snapshotWriters, transformers, sink)
     * // 第 3 个参数位置需要 List<DocumentTransformer>
     * // 第 4 个参数位置需要 List<DocumentWriter>
     *
     * new MetaEtlUpsertPipeline(request, reader, transformers, sink)
     * // 少传 snapshotWriters，除非额外提供 4 参数重载构造器
     * }</pre>
     *
     * <p>
     * 变量名不参与构造器匹配
     * <pre>{@code
     * DocumentIndexingRequest a = request
     * DocumentReader b = reader
     * List<DocumentTransformer> c = transformers
     * List<DocumentWriter> d = snapshotWriters
     * MetaVectorStoreSink e = sink
     *
     * new MetaEtlUpsertPipeline(a, b, c, d, e)
     * // 可以编译，因为类型和位置匹配
     * }</pre>
     *
     * <p>
     * 如果希望少传 snapshotWriters，可以额外写重载构造器
     * record 的重载构造器必须通过 this(...) 委托到主构造器，最终仍要补齐全部 component
     * <pre>{@code
     * public MetaEtlUpsertPipeline(
     *         DocumentIndexingRequest request,
     *         DocumentReader reader,
     *         List<DocumentTransformer> transformers,
     *         MetaVectorStoreSink sink) {
     *     this(request, reader, transformers, List.of(), sink)
     * }
     * }</pre>
     *
     * <p>
     * 它可以理解为下面普通构造器的简写
     * <pre>{@code
     * public MetaEtlUpsertPipeline(
     *         DocumentIndexingRequest request,
     *         DocumentReader reader,
     *         List<DocumentTransformer> transformers,
     *         List<DocumentWriter> snapshotWriters,
     *         MetaVectorStoreSink sink) {
     *     snapshotWriters = snapshotWriters == null ? List.of() : List.copyOf(snapshotWriters);
     *
     *     this.request = request;
     *     this.reader = reader;
     *     this.transformers = transformers;
     *     this.snapshotWriters = snapshotWriters;
     *     this.sink = sink;
     * }
     * }</pre>
     *
     * <p>
     * 这里把 snapshotWriters 做两层保护
     * 第一层：调用方传 null 时归一化为空列表，upsert 执行时不需要额外判空
     * 第二层：调用方传可变 List 时复制为不可变副本，避免 pipeline 创建后执行计划被外部修改
     *
     * <p>
     * 示例
     * <pre>{@code
     * new MetaEtlUpsertPipeline(request, reader, transformers, null, sink)
     * // snapshotWriters() 返回空列表
     *
     * List<DocumentWriter> writers = new ArrayList<>()
     * MetaEtlUpsertPipeline pipeline = new MetaEtlUpsertPipeline(request, reader, transformers, writers, sink)
     * writers.add(otherWriter)
     * // pipeline.snapshotWriters() 不会被外部 writers 后续修改影响
     * }</pre>
     */
    public MetaEtlUpsertPipeline {
        snapshotWriters = snapshotWriters == null ? List.of() : List.copyOf(snapshotWriters);
    }

    /**
     * 执行文档索引计划
     *
     * <p>
     * execute 只表达执行当前 ETL 计划，不把命名绑定到最终写库动作
     * 真正 upsert 语义保留在 MetaVectorStoreSink，用于表达先删旧 chunk 再写新 chunk 的写入策略
     *
     * @return Pipeline 执行结果
     */
    public MetaEtlPipelineResult execute() {
        // read 阶段只解析原始文件，不补业务 metadata，也不做切分
        List<Document> documents = reader.read();
        for (DocumentTransformer transformer : transformers) {
            // transform 阶段按工厂定义的顺序执行，前一个输出就是后一个输入
            documents = transformer.transform(documents);
        }
        for (DocumentWriter snapshotWriter : snapshotWriters) {
            // snapshot writer 只记录本次 ETL 输出快照，不替代最终 VectorStore 写入
            snapshotWriter.write(documents);
        }
        // upsert 阶段先删除同 documentId 的旧 chunk，再写入本次生成的新 chunk
        sink.upsert(documents);
        return new MetaEtlPipelineResult(documents.size());
    }
}
