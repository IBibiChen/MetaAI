package com.metax.rag.etl.transformer;

import com.metax.rag.indexing.DocumentIndexingRequest;
import com.metax.rag.model.DocumentVisibility;
import com.metax.rag.model.MetadataKeys;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MetaDocumentMetadataTransformer .
 *
 * <p>
 * MetaAI 文档级 metadata Transformer，严格实现 Spring AI DocumentTransformer 接口
 * 负责为原始 Document 补齐租户、知识库、文档和来源字段
 *
 * <p>
 * metadata 是企业级 RAG 的权限边界和召回边界
 * 如果只做向量相似度搜索，不加 tenantId 和 kbId 过滤，不同租户或不同知识库的数据可能被一起召回
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
public class MetaDocumentMetadataTransformer implements DocumentTransformer {

    private final DocumentIndexingRequest request;

    public MetaDocumentMetadataTransformer(DocumentIndexingRequest request) {
        validate(request);
        this.request = request;
    }

    /**
     * 补齐文档级 metadata
     *
     * <p>
     * 该方法是 Spring AI DocumentTransformer 的标准入口
     * createdAt 使用 epoch millis，方便 Redis NUMERIC 字段和其他向量库做范围过滤
     *
     * @param documents 原始 Document 列表
     * @return 补齐文档级 metadata 的 Document 列表
     */
    @Override
    @NonNull
    public List<Document> apply(@NonNull List<Document> documents) {
        long createdAt = Instant.now().toEpochMilli();
        return documents.stream()
                .map(document -> withDocumentMetadata(document, createdAt))
                .toList();
    }

    /**
     * 为原始 Document 补齐文档级 metadata
     *
     * <p>
     * 该方法位于 chunk 切分前，负责把租户、知识库、文档、类型、来源和创建时间写入原始 Document
     *
     * @param document  原始 Document
     * @param createdAt 创建时间
     * @return 补齐文档级 metadata 的 Document
     */
    private Document withDocumentMetadata(Document document, long createdAt) {
        // 复制 Reader 已经写入的 metadata，避免覆盖官方 Reader 自带字段
        Map<String, Object> metadata = new HashMap<>(document.getMetadata());
        // scope 标记知识库文档，避免会话文件临时索引被普通 RAG 检索召回
        metadata.put(MetadataKeys.SCOPE, MetadataKeys.SCOPE_KNOWLEDGE);
        // tenantId 和 kbId 是检索强过滤边界，缺失会导致跨租户或跨知识库召回
        metadata.put(MetadataKeys.TENANT_ID, request.tenantId());
        metadata.put(MetadataKeys.KB_ID, request.kbId());
        DocumentVisibility visibility = request.resolvedVisibility();
        metadata.put(MetadataKeys.VISIBILITY, visibility.name());
        metadata.put(MetadataKeys.DEPT_ID, blankToEmpty(request.deptId()));
        metadata.put(MetadataKeys.USER_ID, blankToEmpty(request.userId()));
        // documentId 是幂等覆盖边界，后续 upsert 会按它删除旧 chunk
        metadata.put(MetadataKeys.DOCUMENT_ID, request.documentId());
        // documentType 和 source 用于检索收窄、引用展示和问题排查
        metadata.put(MetadataKeys.DOCUMENT_TYPE, request.documentType());
        metadata.put(MetadataKeys.SOURCE, request.source());
        metadata.put(MetadataKeys.DOCUMENT_NAME, blankToEmpty(request.documentName()));
        // createdAt 使用统一时间戳，保证同一次索引生成的所有原始 Document 时间一致
        metadata.put(MetadataKeys.CREATED_AT, createdAt);
        Document enriched = rebuildDocument(document, metadata, document.getId());
        // Document builder 不会自动继承 contentFormatter，需要显式恢复
        enriched.setContentFormatter(document.getContentFormatter());
        return enriched;
    }

    /**
     * 基于原 Document 重建带新 metadata 的 Document
     *
     * <p>
     * Document 是不可变对象，更新 metadata 时需要重新构造
     * 这里保留原 text / media 和 score，contentFormatter 在外层恢复
     *
     * @param document   原始 Document
     * @param metadata   新 metadata
     * @param documentId 文档 ID
     * @return 重建后的 Document
     */
    private Document rebuildDocument(Document document, Map<String, Object> metadata, String documentId) {
        Document.Builder builder = Document.builder()
                .id(documentId)
                .metadata(metadata)
                .score(document.getScore());
        if (document.isText()) {
            return builder.text(document.getText()).build();
        }
        return builder.media(document.getMedia()).build();
    }

    private String blankToEmpty(String value) {
        return StringUtils.hasText(value) ? value : "";
    }

    private void validate(DocumentIndexingRequest request) {
        // 这些字段都是检索过滤和幂等覆盖的必要字段，缺失时继续写入会污染知识库
        if (!StringUtils.hasText(request.tenantId())) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (!StringUtils.hasText(request.kbId())) {
            throw new IllegalArgumentException("kbId must not be blank");
        }
        DocumentVisibility visibility = request.resolvedVisibility();
        if (visibility == DocumentVisibility.DEPT && !StringUtils.hasText(request.deptId())) {
            throw new IllegalArgumentException("deptId must not be blank when visibility is DEPT");
        }
        if (visibility == DocumentVisibility.USER && !StringUtils.hasText(request.userId())) {
            throw new IllegalArgumentException("userId must not be blank when visibility is USER");
        }
        if (!StringUtils.hasText(request.documentId())) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
        if (!StringUtils.hasText(request.documentType())) {
            throw new IllegalArgumentException("documentType must not be blank");
        }
        if (!StringUtils.hasText(request.source())) {
            throw new IllegalArgumentException("source must not be blank");
        }
    }
}
