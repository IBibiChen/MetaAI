package com.metax.rag.pipeline;

import com.metax.rag.indexing.DocumentIndexingContext;
import com.metax.rag.indexing.DocumentIndexingRun;
import com.metax.rag.indexing.DocumentIndexingRunObserver;
import com.metax.rag.indexing.DocumentIndexingStatus;
import com.metax.rag.indexing.DocumentIndexingRunRepository;
import cn.hutool.core.date.TimeInterval;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
    @Async("taskExecutor")
    public void runIndexing(DocumentIndexingRun run, DocumentIndexingContext context) {
        TimeInterval timer = new TimeInterval();
        saveAndNotify(run.withStatus(DocumentIndexingStatus.RUNNING, 0, "RAG document indexing started"));
        log.info("RAG 文档索引开始：runId = {}，tenantId = {}，kbId = {}，documentId = {}，documentType = {}",
                run.runId(), run.tenantId(), run.kbId(), run.documentId(), run.documentType());
        try {
            MetaEtlUpsertPipeline pipeline = pipelineFactory.createIndexingPipeline(context);
            MetaEtlPipelineResult result = pipeline.execute();
            saveAndNotify(run.withStatus(DocumentIndexingStatus.SUCCEEDED, result.chunkCount(),
                    "RAG document indexing succeeded"));
            log.info("RAG 文档索引完成：runId = {}，documentId = {}，chunkCount = {}，durationMs = {}",
                    run.runId(), run.documentId(), result.chunkCount(), timer.intervalMs());
        } catch (Throwable ex) {
            handleIndexingFailure(run, timer, ex);
        }
    }

    /**
     * 处理文档索引失败
     *
     * <p>
     * 异步线程中 StackOverflowError 等 Error 不属于 Exception
     * 如果不兜底写入 FAILED，外部同步 Worker 会一直等待对象存储文档从 INDEXING 进入终态
     *
     * @param run   文档索引 run
     * @param timer 本次执行计时器
     * @param ex    索引异常或错误
     */
    private void handleIndexingFailure(DocumentIndexingRun run, TimeInterval timer, Throwable ex) {
        // 所有 Throwable 都先落失败状态
        saveAndNotify(run.withStatus(DocumentIndexingStatus.FAILED, 0, failureMessage(ex)));
        log.warn("RAG 文档索引失败：runId = {}，documentId = {}，durationMs = {}，exception = {}: {}",
                run.runId(), run.documentId(), timer.intervalMs(), ex.getClass().getSimpleName(),
                ex.getMessage(), ex);

        // JVM 级严重错误再继续抛出
        if (ex instanceof VirtualMachineError error) {
            throw error;
        }
        if (ex instanceof ThreadDeath error) {
            throw error;
        }
    }

    /**
     * 生成失败状态说明
     *
     * <p>
     * 部分 Error 的 message 为空，状态说明至少保留异常类型，避免排查时只看到空错误
     *
     * @param ex 索引异常或错误
     * @return 失败状态说明
     */
    private String failureMessage(Throwable ex) {
        if (ex.getMessage() == null || ex.getMessage().isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return ex.getClass().getSimpleName() + ": " + ex.getMessage();
    }

    /**
     * 保存 run 并通知观察者
     *
     * @param run 文档索引 run
     */
    private void saveAndNotify(DocumentIndexingRun run) {
        runRepository.save(run);
        runObservers.forEach(observer -> observer.onRunChanged(run));
    }
}
