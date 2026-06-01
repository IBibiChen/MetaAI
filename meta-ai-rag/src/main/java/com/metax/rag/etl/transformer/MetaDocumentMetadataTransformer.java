package com.metax.rag.etl.transformer;

import com.metax.rag.indexing.DocumentIndexingRequest;
import com.metax.rag.model.MetadataKeys;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
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
 * 如果只做向量相似度搜索，不加 tenantId 和 knowledgeBaseId 过滤，不同租户或不同知识库的数据可能被一起召回
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
    public List<Document> apply(List<Document> documents) {
        long createdAt = Instant.now().toEpochMilli();
        return documents.stream()
                .map(document -> withDocumentMetadata(document, createdAt))
                .toList();
    }

    private Document withDocumentMetadata(Document document, long createdAt) {
        Map<String, Object> metadata = new HashMap<>(document.getMetadata());
        metadata.put(MetadataKeys.TENANT_ID, request.tenantId());
        metadata.put(MetadataKeys.KNOWLEDGE_BASE_ID, request.knowledgeBaseId());
        metadata.put(MetadataKeys.DOCUMENT_ID, request.documentId());
        metadata.put(MetadataKeys.DOCUMENT_TYPE, request.documentType());
        metadata.put(MetadataKeys.SOURCE, request.source());
        metadata.put(MetadataKeys.CREATED_AT, createdAt);
        Document enriched = copyDocument(document, metadata, document.getId());
        enriched.setContentFormatter(document.getContentFormatter());
        return enriched;
    }

    private Document copyDocument(Document document, Map<String, Object> metadata, String documentId) {
        Document.Builder builder = Document.builder()
                .id(documentId)
                .metadata(metadata)
                .score(document.getScore());
        if (document.isText()) {
            return builder.text(document.getText()).build();
        }
        return builder.media(document.getMedia()).build();
    }

    private void validate(DocumentIndexingRequest request) {
        // 这些字段都是检索过滤和幂等覆盖的必要字段，缺失时继续写入会污染知识库
        if (!StringUtils.hasText(request.tenantId())) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (!StringUtils.hasText(request.knowledgeBaseId())) {
            throw new IllegalArgumentException("knowledgeBaseId must not be blank");
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

