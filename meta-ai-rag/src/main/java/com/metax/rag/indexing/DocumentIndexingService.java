package com.metax.rag.indexing;

import com.metax.rag.etl.model.DocumentSourceType;
import com.metax.rag.etl.resource.MetaDocumentResource;
import com.metax.rag.etl.resource.MetaDocumentResourceFactory;
import com.metax.rag.model.EmbeddingProvider;
import com.metax.rag.model.VectorStoreBackend;
import com.metax.rag.pipeline.MetaEtlPipeline;
import com.metax.rag.storage.RustFsStorageService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * DocumentIndexingService .
 *
 * <p>
 * RAG 文档索引门面服务，负责把已归档文件转换为异步索引任务
 * 真正 ETL 执行交给 MetaEtlPipeline，避免 @Async 被同类自调用绕过
 *
 * <p>
 * 设计说明：索引门面和 ETL Worker 必须分开
 * Spring 的 @Async 依赖代理对象生效，如果在同一个类里 this.ingest 调用异步方法，调用不会经过代理
 * 所以当前类只负责快速返回 job，耗时的解析、切分、embedding 和写库交给 MetaEtlPipeline
 *
 * <p>
 * 当前索引采用异步任务模型，RAG 只消费已经归档好的对象存储对象或受控本地文件
 * 调用方先完成原始文件归档，再把 job 状态写入 Redis，最后触发后台 ETL
 * 这样 HTTP 请求不会被大文件解析、模型 embedding 或向量库写入长时间阻塞
 *
 * <p>
 * 对象存储对象导入示例
 * <pre>{@code
 * curl -X POST http://localhost:8008/v1/rag/documents/import
 *   -d provider=dashscope
 *   -d vectorStore=redis
 *   -d tenantId=t1
 *   -d knowledgeBaseId=kb1
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

    private final RustFsStorageService storageService;

    private final DocumentIndexingJobRepository jobRepository;

    private final MetaEtlPipeline etlPipeline;

    private final MetaDocumentResourceFactory documentResourceFactory;

    public DocumentIndexingService(RustFsStorageService storageService,
                                   DocumentIndexingJobRepository jobRepository,
                                   MetaEtlPipeline etlPipeline,
                                   MetaDocumentResourceFactory documentResourceFactory) {
        this.storageService = storageService;
        this.jobRepository = jobRepository;
        this.etlPipeline = etlPipeline;
        this.documentResourceFactory = documentResourceFactory;
    }

    /**
     * 从受控本地目录创建 RAG 文档索引任务
     *
     * <p>
     * localPath 必须是相对 metax.ai.rag.storage.local-root 的路径
     * documentType 可以为空，为空时根据 localPath 后缀自动识别
     *
     * @param provider        provider
     * @param vectorStore     向量库后端
     * @param tenantId        租户 ID
     * @param knowledgeBaseId 知识库 ID
     * @param documentId      文档 ID
     * @param documentType    文档类型
     * @param localPath       本地文件相对路径
     * @param source          来源标识
     * @return 文档索引任务
     */
    public DocumentIndexingJob importLocalFile(String provider,
                                               String vectorStore,
                                               String tenantId,
                                               String knowledgeBaseId,
                                               String documentId,
                                               String documentType,
                                               String localPath,
                                               String source) {
        DocumentIndexingRequest request = new DocumentIndexingRequest(EmbeddingProvider.from(provider),
                VectorStoreBackend.from(vectorStore), tenantId, knowledgeBaseId, documentId, documentType,
                DocumentSourceType.LOCAL_FILE, source, null, null, localPath);
        return submit(request);
    }

    /**
     * 从已归档对象或受控本地文件创建 RAG 文档索引任务
     *
     * <p>
     * 这是对象存储和本地文件统一进入 Spring AI ETL 的边界
     * submit 只接收可解析的资源描述，不负责保存原始文件
     *
     * @param request 文档索引请求
     * @return 文档索引任务
     */
    public DocumentIndexingJob submit(DocumentIndexingRequest request) {
        // 资源工厂负责把对象存储 objectKey 或本地相对路径转换为 Spring Resource
        MetaDocumentResource documentResource = documentResourceFactory.create(request);
        // documentType 和 source 在资源解析后落定，后续 job、metadata 和 Reader 使用同一份值
        DocumentIndexingRequest resolvedRequest = request.withResolvedDocument(documentResource.documentType(),
                documentResource.source());
        DocumentIndexingContext context = new DocumentIndexingContext(resolvedRequest, documentResource);
        Instant now = Instant.now();
        DocumentIndexingJob job = new DocumentIndexingJob(UUID.randomUUID().toString(), DocumentIndexingStatus.PENDING,
                resolvedRequest.tenantId(), resolvedRequest.knowledgeBaseId(), resolvedRequest.documentId(),
                resolvedRequest.documentType(), resolvedRequest.provider().apiName(), resolvedRequest.vectorStore().apiName(),
                resolvedRequest.bucket(), resolvedRequest.objectKey(),
                0, "RAG document indexing submitted", now, now);
        jobRepository.save(job);
        etlPipeline.upsert(job, context);
        return job;
    }

    /**
     * 查询 RAG 文档索引任务
     *
     * <p>
     * API 示例
     * <pre>{@code
     * curl http://localhost:8008/v1/rag/documents/jobs/{jobId}
     * }</pre>
     *
     * @param jobId 任务 ID
     * @return 文档索引任务
     */
    public DocumentIndexingJob getJob(String jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("RAG document indexing job not found: " + jobId));
    }
}
