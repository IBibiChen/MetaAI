package com.metax.rag.pipeline;

import com.metax.rag.indexing.DocumentIndexingContext;
import com.metax.rag.indexing.DocumentIndexingRun;
import com.metax.rag.indexing.DocumentIndexingRunObserver;
import com.metax.rag.indexing.DocumentIndexingStatus;
import com.metax.rag.indexing.DocumentIndexingRunRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MetaEtlPipeline .
 *
 * <p>
 * RAG 异步文档索引调度器，负责 run 状态流转，并执行已组装好的索引 pipeline
 *
 * <p>
 * 链路说明：Spring AI ETL 标准链路
 * DocumentReader 负责把外部数据源解析成 Document
 * DocumentTransformer 负责对 Document 做转换，例如切分、清洗、补 metadata
 * DocumentWriter 负责写入目标系统，VectorStore 本身就是 DocumentWriter
 *
 * <p>
 * 当前类不直接组装 Reader、Transformer 和 Writer
 * 具体装配交给 MetaEtlPipelineFactory，具体执行交给 MetaEtlUpsertPipeline
 * 当前类不暴露 upsert 命名，是为了避免和 VectorStore Sink 的写入策略混淆
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
@Component
public class MetaEtlPipeline {

    private final MetaEtlPipelineFactory pipelineFactory;

    private final DocumentIndexingRunRepository runRepository;

    private final List<DocumentIndexingRunObserver> runObservers;

    public MetaEtlPipeline(MetaEtlPipelineFactory pipelineFactory,
                           DocumentIndexingRunRepository runRepository,
                           ObjectProvider<DocumentIndexingRunObserver> runObservers) {
        this.pipelineFactory = pipelineFactory;
        this.runRepository = runRepository;
        this.runObservers = runObservers.orderedStream().toList();
    }

    /**
     * 异步执行文档索引 run
     *
     * <p>
     * 执行步骤
     * 1. 把 run 标记为 RUNNING
     * 2. 创建 indexing pipeline
     * 3. 执行 pipeline，内部完成 read、transform、delete、write
     * 4. 根据执行结果更新 run 状态
     *
     * @param run     文档索引 run
     * @param context 文档索引上下文
     */
    @Async
    public void runIndexing(DocumentIndexingRun run, DocumentIndexingContext context) {
        saveAndNotify(run.withStatus(DocumentIndexingStatus.RUNNING, 0, "RAG document indexing started"));
        try {
            MetaEtlUpsertPipeline pipeline = pipelineFactory.createIndexingPipeline(context);
            MetaEtlPipelineResult result = pipeline.execute();
            saveAndNotify(run.withStatus(DocumentIndexingStatus.SUCCEEDED, result.chunkCount(),
                    "RAG document indexing succeeded"));
        } catch (Exception ex) {
            saveAndNotify(run.withStatus(DocumentIndexingStatus.FAILED, 0, ex.getMessage()));
        }
    }

    private void saveAndNotify(DocumentIndexingRun run) {
        runRepository.save(run);
        runObservers.forEach(observer -> observer.onRunChanged(run));
    }
}
