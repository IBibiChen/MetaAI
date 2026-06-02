package com.metax.rag.pipeline;

import com.metax.rag.indexing.DocumentIndexingJob;
import com.metax.rag.indexing.DocumentIndexingContext;
import com.metax.rag.indexing.DocumentIndexingStatus;
import com.metax.rag.indexing.DocumentIndexingJobRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * MetaEtlPipeline .
 *
 * <p>
 * RAG 异步文档索引调度器，负责 job 状态流转，并执行已组装好的索引 pipeline
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

    private final DocumentIndexingJobRepository jobRepository;

    public MetaEtlPipeline(MetaEtlPipelineFactory pipelineFactory,
                           DocumentIndexingJobRepository jobRepository) {
        this.pipelineFactory = pipelineFactory;
        this.jobRepository = jobRepository;
    }

    /**
     * 异步执行文档索引任务
     *
     * <p>
     * 执行步骤
     * 1. 把 job 标记为 RUNNING
     * 2. 创建 indexing pipeline
     * 3. 执行 pipeline，内部完成 read、transform、delete、write
     * 4. 根据执行结果更新 job 状态
     *
     * @param job     文档索引任务
     * @param context 文档索引上下文
     */
    @Async
    public void runIndexing(DocumentIndexingJob job, DocumentIndexingContext context) {
        jobRepository.save(job.withStatus(DocumentIndexingStatus.RUNNING, 0, "RAG document indexing started"));
        try {
            MetaEtlUpsertPipeline pipeline = pipelineFactory.createIndexingPipeline(context);
            MetaEtlPipelineResult result = pipeline.execute();
            jobRepository.save(job.withStatus(DocumentIndexingStatus.SUCCEEDED, result.chunkCount(),
                    "RAG document indexing succeeded"));
        } catch (Exception ex) {
            jobRepository.save(job.withStatus(DocumentIndexingStatus.FAILED, 0, ex.getMessage()));
        }
    }
}
