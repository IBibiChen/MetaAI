package com.metax.retrieval.indexing;

import com.metax.rag.indexing.DocumentIndexingRun;
import com.metax.retrieval.indexing.request.DocumentImportRequest;
import com.metax.retrieval.indexing.request.LocalDocumentImportRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * KnowledgeDocumentIndexController .
 *
 * <p>
 * 知识库文档索引任务接口
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "知识库文档索引", description = "知识库文档导入、索引任务创建和任务查询接口")
public class KnowledgeDocumentIndexController {

    private final KnowledgeDocumentIndexService knowledgeDocumentIndexService;

    /**
     * 从对象存储既有对象创建异步文档索引执行
     *
     * @param request 对象存储文档索引导入请求参数
     * @return 文档索引执行
     */
    @PostMapping(value = "/v1/rag/documents/import")
    @Operation(summary = "从对象存储创建知识库文档索引任务", description = "原始文件必须已经归档到对象存储，本接口只创建异步 ETL 索引任务")
    public DocumentIndexingRun importObjectStorageDocument(@Valid @ParameterObject DocumentImportRequest request) {
        return knowledgeDocumentIndexService.importObjectStorageDocument(request);
    }

    /**
     * 从受控本地目录创建异步文档索引执行
     *
     * @param request 本地文档索引导入请求参数
     * @return 文档索引执行
     */
    @PostMapping(value = "/v1/rag/documents/import/local")
    @Operation(summary = "从受控本地目录创建知识库文档索引任务", description = "path 必须是 metax.ai.rag.storage.local-root 下的相对路径")
    public DocumentIndexingRun importLocalDocument(@Valid @ParameterObject LocalDocumentImportRequest request) {
        return knowledgeDocumentIndexService.importLocalDocument(request);
    }

    /**
     * 查询异步文档索引执行
     *
     * @param runId 执行 ID
     * @return 文档索引执行
     */
    @GetMapping(value = "/v1/rag/documents/runs/{runId}")
    @Operation(summary = "查询知识库文档索引任务", description = "根据 runId 查询异步索引状态、写入 chunk 数和失败原因")
    public DocumentIndexingRun getDocumentIndexingRun(
            @Parameter(description = "文档索引执行 ID", example = "c2a6bb6d-b0e6-4c40-9f32-3b08b5b19d62",
                    required = true, in = ParameterIn.PATH)
            @PathVariable String runId) {
        return knowledgeDocumentIndexService.getRun(runId);
    }
}
