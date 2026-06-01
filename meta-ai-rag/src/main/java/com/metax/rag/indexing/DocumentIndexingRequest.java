package com.metax.rag.indexing;

import com.metax.rag.etl.model.DocumentSourceType;
import com.metax.rag.model.EmbeddingProvider;
import com.metax.rag.model.VectorStoreBackend;

/**
 * DocumentIndexingRequest .
 *
 * <p>
 * RAG 文档索引请求，调用方必须显式选择 embedding provider 和 vectorStore，避免不同 embedding 语义空间混写
 *
 * <p>
 * 字段说明：文档索引请求决定数据写到哪个语义空间
 * provider 决定使用哪套 EmbeddingModel，不代表 ChatModel provider
 * vectorStore 决定写入 Redis、Qdrant 还是 Milvus
 * tenantId 和 knowledgeBaseId 决定后续检索过滤边界
 * documentId 决定重复上传时覆盖哪份文档的旧 chunk
 * sourceType 决定文件来自对象存储文件流还是受控本地目录
 *
 * <p>
 * 示例
 * <pre>{@code
 * new DocumentIndexingRequest(
 *     EmbeddingProvider.DASHSCOPE,
 *     VectorStoreBackend.REDIS,
 *     "t1",
 *     "kb1",
 *     "doc-001",
 *     "markdown",
 *     DocumentSourceType.OBJECT_STORAGE,
 *     "knowledge/t1/kb1/demo.md",
 *     "meta-ai-knowledge",
 *     "knowledge/t1/kb1/demo.md",
 *     null)
 * }</pre>
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
public record DocumentIndexingRequest(
        /**
         * embedding provider，决定文档索引时使用哪套 EmbeddingModel
         */
        EmbeddingProvider provider,
        /**
         * 向量库后端，决定写入 Redis、Qdrant 还是 Milvus
         */
        VectorStoreBackend vectorStore,
        /**
         * 租户 ID，后续检索必须用它做隔离过滤
         */
        String tenantId,
        /**
         * 知识库 ID，后续检索必须用它限定知识范围
         */
        String knowledgeBaseId,
        /**
         * 文档 ID，重复索引时按它删除旧 chunk
         */
        String documentId,
        /**
         * 文档类型，用于选择 Reader 和后续检索过滤
         *
         * <p>
         * 允许调用方为空，提交文档索引任务前会根据文件名或 objectKey 自动识别
         */
        String documentType,
        /**
         * 文档来源类型，只区分对象存储文件流和受控本地文件
         */
        DocumentSourceType sourceType,
        /**
         * 文档来源，通常是对象存储 objectKey、本地相对路径或业务来源路径
         */
        String source,
        /**
         * 对象存储 bucket 名称
         */
        String bucket,
        /**
         * 对象存储 object key
         */
        String objectKey,
        /**
         * 本地文件相对路径，必须位于 metax.ai.rag.storage.local-root 下
         */
        String localPath
) {

    /**
     * 返回用于推断 documentType 的文件名
     *
     * @return 文件名或路径
     */
    public String typeHint() {
        return sourceType == DocumentSourceType.LOCAL_FILE ? localPath : objectKey;
    }

    /**
     * 创建带最终 documentType 和 source 的请求
     *
     * @param resolvedDocumentType 最终文档类型
     * @param resolvedSource       最终来源
     * @return RAG 文档索引请求
     */
    public DocumentIndexingRequest withResolvedDocument(String resolvedDocumentType, String resolvedSource) {
        return new DocumentIndexingRequest(provider, vectorStore, tenantId, knowledgeBaseId, documentId,
                resolvedDocumentType, sourceType, resolvedSource, bucket, objectKey, localPath);
    }
}
