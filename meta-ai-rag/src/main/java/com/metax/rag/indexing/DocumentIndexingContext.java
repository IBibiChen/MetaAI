package com.metax.rag.indexing;

import com.metax.rag.etl.resource.MetaDocumentResource;

/**
 * DocumentIndexingContext .
 *
 * <p>
 * RAG 文档索引上下文，保存提交阶段已经解析完成的请求和文档资源
 * 后续 Pipeline 直接复用该上下文，避免重复创建 Resource 和重复解析 documentType
 *
 * @param request          已解析 documentType 和 source 的文档索引请求
 * @param documentResource 文档资源
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/1
 */
public record DocumentIndexingContext(
        DocumentIndexingRequest request,
        MetaDocumentResource documentResource
) {
}
