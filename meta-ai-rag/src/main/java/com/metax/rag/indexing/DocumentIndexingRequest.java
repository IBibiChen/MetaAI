package com.metax.rag.indexing;

import com.metax.rag.model.DocumentVisibility;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * DocumentIndexingRequest .
 *
 * <p>
 * RAG 文档索引请求，调用方只提交文档来源和业务过滤字段
 *
 * <p>
 * 字段说明：文档索引请求不再决定模型和向量库
 * EmbeddingModel 由 spring.ai.model.embedding 选择
 * VectorStore 由 spring.ai.vectorstore.type 选择
 * tenantId 和 kbId 决定后续检索过滤边界
 * documentId 决定重复上传时覆盖哪份文档的旧 chunk
 * bucket 和 objectKey 决定从对象存储读取哪份原始文档
 *
 * <p>
 * 示例
 * <pre>{@code
 * DocumentIndexingRequest.builder()
 *         .tenantId("t1")
 *         .kbId("kb1")
 *         .documentId("doc-001")
 *         .documentType("markdown")
 *         .source("knowledge/t1/kb1/demo.md")
 *         .bucket("meta-ai-knowledge")
 *         .objectKey("knowledge/t1/kb1/demo.md")
 *         .build()
 * }</pre>
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
@Getter
@Builder
@Accessors(fluent = true)
@EqualsAndHashCode
@ToString
public final class DocumentIndexingRequest {

    /**
     * 租户 ID，后续检索必须用它做隔离过滤
     */
    private final String tenantId;

    /**
     * 知识库 ID，后续检索必须用它限定知识范围
     */
    private final String kbId;

    /**
     * 文档 ID，重复索引时按它删除旧 chunk
     */
    private final String documentId;

    /**
     * 文档可见性，默认为 PUBLIC
     */
    private final String visibility;

    /**
     * 部门 ID，visibility=DEPT 时必填
     */
    private final String deptId;

    /**
     * 用户 ID，visibility=USER 时必填
     */
    private final String userId;

    /**
     * 文档类型，用于选择 Reader 和后续检索过滤
     *
     * <p>
     * 允许调用方为空，提交文档索引执行前会根据 objectKey 自动识别
     */
    private final String documentType;

    /**
     * 文档来源，通常是对象存储 objectKey 或业务来源路径
     */
    private final String source;

    /**
     * 文档展示名，用于前端展示引用来源
     */
    private final String documentName;

    /**
     * 对象存储 bucket 名称
     */
    private final String bucket;

    /**
     * 对象存储 object key
     */
    private final String objectKey;

    /**
     * 返回解析后的文档可见性
     *
     * @return 文档可见性
     */
    public DocumentVisibility resolvedVisibility() {
        return DocumentVisibility.resolve(visibility);
    }

    /**
     * 创建带最终 documentType 和 source 的请求
     *
     * @param resolvedDocumentType 最终文档类型
     * @param resolvedSource       最终来源
     * @return RAG 文档索引请求
     */
    public DocumentIndexingRequest withResolvedDocument(String resolvedDocumentType, String resolvedSource) {
        return DocumentIndexingRequest.builder()
                .tenantId(tenantId)
                .kbId(kbId)
                .documentId(documentId)
                .visibility(visibility)
                .deptId(deptId)
                .userId(userId)
                .documentType(resolvedDocumentType)
                .source(resolvedSource)
                .documentName(documentName)
                .bucket(bucket)
                .objectKey(objectKey)
                .build();
    }
}
