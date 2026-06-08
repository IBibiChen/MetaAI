package com.metax.retrieval.indexing;

import com.metax.rag.indexing.DocumentIndexingRun;
import com.metax.rag.indexing.DocumentIndexingService;
import com.metax.retrieval.indexing.request.DocumentImportRequest;
import com.metax.retrieval.indexing.request.LocalDocumentImportRequest;
import com.metax.retrieval.indexing.support.DocumentIndexingRequestFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * KnowledgeDocumentIndexService .
 *
 * <p>
 * 知识库文档索引任务编排服务
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentIndexService {

    private final DocumentIndexingService documentIndexingService;

    private final DocumentIndexingRequestFactory documentIndexingRequestFactory;

    /**
     * 从对象存储既有对象创建文档索引任务
     *
     * @param request 对象存储文档索引导入请求参数
     * @return 文档索引执行
     */
    public DocumentIndexingRun importObjectStorageDocument(DocumentImportRequest request) {
        // 对象存储导入只负责把接口参数转成核心索引请求，实际异步执行交给 meta-ai-rag 的索引服务
        return documentIndexingService.submit(documentIndexingRequestFactory.objectStorageRequest(request));
    }

    /**
     * 从受控本地目录创建文档索引任务
     *
     * @param request 本地文档索引导入请求参数
     * @return 文档索引执行
     */
    public DocumentIndexingRun importLocalDocument(LocalDocumentImportRequest request) {
        // 本地导入必须经过核心服务的受控目录校验，避免接口层直接读取任意本地路径
        return documentIndexingService.importLocalFile(request.getTenantId(), request.getKbId(),
                request.getDocumentId(), documentIndexingRequestFactory.visibility(request.getVisibility()),
                request.getDeptId(), request.getUserId(), request.getDocumentType(), request.getPath(),
                request.getSource());
    }

    /**
     * 查询文档索引执行
     *
     * @param runId 执行 ID
     * @return 文档索引执行
     */
    public DocumentIndexingRun getRun(String runId) {
        // 查询直接委托核心索引服务，接口层不缓存索引执行状态
        return documentIndexingService.getRun(runId);
    }
}
