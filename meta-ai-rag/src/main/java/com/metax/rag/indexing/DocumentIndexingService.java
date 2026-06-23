package com.metax.rag.indexing;

import com.metax.rag.etl.resource.MetaDocumentResource;
import com.metax.rag.etl.resource.MetaDocumentResourceFactory;
import com.metax.rag.pipeline.MetaEtlPipeline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * DocumentIndexingService .
 *
 * <p>
 * RAG 文档索引门面服务，负责把已归档文件转换为异步索引执行
 * 真正 ETL 执行交给 MetaEtlPipeline，避免 @Async 被同类自调用绕过
 *
 * <p>
 * 设计说明：索引门面和 ETL Worker 必须分开
 * Spring 的 @Async 依赖代理对象生效，如果在同一个类里直接调用本类异步方法，调用不会经过代理
 * 所以当前类只负责快速返回 run，耗时的解析、切分、embedding 和写库交给独立的 MetaEtlPipeline Bean
 *
 * <p>
 * 当前索引采用异步执行模型，RAG 只消费已经通过 storage 模块归档好的对象存储文档
 * 调用方先完成原始文件归档和文档元数据保存，再把 run 状态写入 Redis，最后触发后台 ETL
 * 这样 HTTP 请求不会被大文件解析、模型 embedding 或向量库写入长时间阻塞
 *
 * <p>
 * 标准索引入口示例
 * <pre>{@code
 * curl -X POST "http://localhost:8008/v1/storage/documents/{documentId}/index?tenantId=t1&kbId=kb1"
 * }</pre>
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
@Slf4j
@Service
public class DocumentIndexingService {

    private final DocumentIndexingRunRepository runRepository;

    private final MetaEtlPipeline etlPipeline;

    private final MetaDocumentResourceFactory documentResourceFactory;

    public DocumentIndexingService(DocumentIndexingRunRepository runRepository,
                                   MetaEtlPipeline etlPipeline,
                                   MetaDocumentResourceFactory documentResourceFactory) {
        this.runRepository = runRepository;
        this.etlPipeline = etlPipeline;
        this.documentResourceFactory = documentResourceFactory;
    }

    /**
     * 提交已归档对象存储文档的 RAG 文档索引执行
     *
     * <p>
     * beforeRun 在后台索引启动前执行，用于调用方持久化 runId 等业务关联关系
     * 当前 storage 文档索引链路必须在该回调中绑定 latestIndexingRunId，便于后续同步索引状态
     * 本方法只提交 run，不启动 ETL，有事务边界的调用方应在事务提交后调用 start
     *
     * @param request   文档索引请求
     * @param beforeRun 后台索引启动前业务绑定回调
     * @return 索引提交结果
     */
    public DocumentIndexingSubmission submit(DocumentIndexingRequest request, Consumer<DocumentIndexingRun> beforeRun) {
        // 阶段 1：解析对象存储文件资源，把 objectKey 对应的文件流统一转换为 Spring Resource
        MetaDocumentResource documentResource = documentResourceFactory.create(request);

        // 阶段 2：落定 documentType 和 source，后续 run、metadata、Reader 都使用这份解析结果
        DocumentIndexingRequest resolvedRequest = request.withResolvedDocument(documentResource.documentType(),
                documentResource.source());

        // 阶段 3：创建索引上下文，避免异步 Worker 重复创建 Resource 或重复推断 documentType
        DocumentIndexingContext context = new DocumentIndexingContext(resolvedRequest, documentResource);

        // 阶段 4：创建 PENDING 执行快照，调用方可以用 runId 查询异步索引状态
        DocumentIndexingRun run = DocumentIndexingRun.pending(resolvedRequest);

        // 阶段 5：先保存执行，后续启动 ETL 时可以通过 runId 查询状态
        runRepository.save(run);

        // 阶段 6：异步启动前允许调用方保存 runId，避免后台执行状态回写时找不到业务记录
        beforeRun.accept(run);

        log.info("RAG 文档索引 run 已提交：runId = {}，tenantId = {}，kbId = {}，documentId = {}，documentType = {}",
                run.runId(), run.tenantId(), run.kbId(), run.documentId(), run.documentType());
        return new DocumentIndexingSubmission(run, context);
    }

    /**
     * 启动已提交的后台索引执行
     *
     * <p>
     * storage 等有事务边界的调用方必须在事务提交后调用本方法
     *
     * @param submission 索引提交结果
     */
    public void start(DocumentIndexingSubmission submission) {
        // 阶段 7：触发后台索引，内部执行 read -> transform -> snapshot -> VectorStore upsert
        etlPipeline.runIndexing(submission.run(), submission.context());
        log.info("RAG 文档索引后台启动已提交：runId = {}，tenantId = {}，kbId = {}，documentId = {}，documentType = {}",
                submission.run().runId(), submission.run().tenantId(), submission.run().kbId(),
                submission.run().documentId(), submission.run().documentType());
    }

    /**
     * 查询 RAG 文档索引执行
     *
     * <p>
     * StorageDocumentService 会根据 latestIndexingRunId 调用该方法同步文档索引状态
     *
     * @param runId 执行 ID
     * @return 文档索引执行
     */
    public DocumentIndexingRun getRun(String runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("RAG document indexing run not found: " + runId));
    }
}
