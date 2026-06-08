package com.metax.rag.indexing;

import com.metax.rag.etl.model.DocumentSourceType;
import com.metax.rag.etl.resource.MetaDocumentResource;
import com.metax.rag.etl.resource.MetaDocumentResourceFactory;
import com.metax.rag.pipeline.MetaEtlPipeline;
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
 * 当前索引采用异步执行模型，RAG 只消费已经归档好的对象存储对象或受控本地文件
 * 调用方先完成原始文件归档，再把 run 状态写入 Redis，最后触发后台 ETL
 * 这样 HTTP 请求不会被大文件解析、模型 embedding 或向量库写入长时间阻塞
 *
 * <p>
 * 对象存储对象导入示例
 * <pre>{@code
 * curl -X POST http://localhost:8008/v1/rag/documents/import
 *   -d tenantId=t1
 *   -d kbId=kb1
 *   -d documentId=doc-001
 *   -d source=knowledge/t1/kb1/2026/05/demo.md
 *   -d bucket=meta-ai-knowledge
 *   -d objectKey=knowledge/t1/kb1/2026/05/demo.md
 * }</pre>
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
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
     * 从受控本地目录创建 RAG 文档索引执行
     *
     * <p>
     * localPath 必须是相对 metax.ai.rag.storage.local-root 的路径
     * documentType 可以为空，为空时根据 localPath 后缀自动识别
     *
     * @param tenantId        租户 ID
     * @param kbId 知识库 ID
     * @param documentId      文档 ID
     * @param visibility      文档可见性
     * @param deptId          部门 ID
     * @param userId          用户 ID
     * @param documentType    文档类型
     * @param localPath       本地文件相对路径
     * @param source          来源标识
     * @return 文档索引执行
     */
    public DocumentIndexingRun importLocalFile(String tenantId,
                                               String kbId,
                                               String documentId,
                                               String visibility,
                                               String deptId,
                                               String userId,
                                               String documentType,
                                               String localPath,
                                               String source) {
        // 阶段 1：把 Controller 传入的字符串参数适配成项目内部索引请求
        DocumentIndexingRequest request = DocumentIndexingRequest.builder()
                .tenantId(tenantId)
                .kbId(kbId)
                .documentId(documentId)
                .visibility(visibility)
                .deptId(deptId)
                .userId(userId)
                .documentType(documentType)
                .sourceType(DocumentSourceType.LOCAL_FILE)
                .source(source)
                .documentName(documentNameFromPath(localPath))
                .localPath(localPath)
                .build();

        // 阶段 2：本地文件和对象存储文件最终统一进入 submit 索引入口
        return submit(request);
    }

    /**
     * 从已归档对象或受控本地文件创建 RAG 文档索引执行
     *
     * <p>
     * 这是对象存储和本地文件统一进入 Spring AI ETL 的边界
     * submit 只接收可解析的资源描述，不负责保存原始文件
     *
     * @param request 文档索引请求
     * @return 文档索引执行
     */
    public DocumentIndexingRun submit(DocumentIndexingRequest request) {
        return submit(request, run -> {
        });
    }

    /**
     * 从已归档对象或受控本地文件创建 RAG 文档索引执行
     *
     * <p>
     * beforeRun 在异步 Worker 启动前执行，用于调用方持久化 runId 等业务关联关系
     *
     * @param request   文档索引请求
     * @param beforeRun 异步启动前回调
     * @return 文档索引执行
     */
    public DocumentIndexingRun submit(DocumentIndexingRequest request, Consumer<DocumentIndexingRun> beforeRun) {
        // 阶段 1：解析文件资源，统一对象存储 objectKey 和本地相对路径为 Spring Resource
        MetaDocumentResource documentResource = documentResourceFactory.create(request);

        // 阶段 2：落定 documentType 和 source，后续 run、metadata、Reader 都使用这份解析结果
        DocumentIndexingRequest resolvedRequest = request.withResolvedDocument(documentResource.documentType(),
                documentResource.source());

        // 阶段 3：创建索引上下文，避免异步 Worker 重复创建 Resource 或重复推断 documentType
        DocumentIndexingContext context = new DocumentIndexingContext(resolvedRequest, documentResource);

        // 阶段 4：创建 PENDING 执行快照，调用方可以用 runId 查询异步索引状态
        DocumentIndexingRun run = DocumentIndexingRun.pending(resolvedRequest);

        // 阶段 5：先保存执行，再触发异步 Worker，避免异步执行过快导致查询不到 run
        runRepository.save(run);

        // 阶段 6：异步启动前允许调用方保存 runId，避免后台执行状态回写时找不到业务记录
        beforeRun.accept(run);

        // 阶段 7：触发后台索引，内部执行 read -> transform -> snapshot -> VectorStore upsert
        etlPipeline.runIndexing(run, context);

        return run;
    }

    /**
     * 查询 RAG 文档索引执行
     *
     * <p>
     * API 示例
     * <pre>{@code
     * curl http://localhost:8008/v1/rag/documents/runs/{runId}
     * }</pre>
     *
     * @param runId 执行 ID
     * @return 文档索引执行
     */
    public DocumentIndexingRun getRun(String runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("RAG document indexing run not found: " + runId));
    }

    private String documentNameFromPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalized = path.replace("\\", "/");
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }
}
