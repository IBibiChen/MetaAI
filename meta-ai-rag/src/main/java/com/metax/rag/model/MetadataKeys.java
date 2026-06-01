package com.metax.rag.model;

/**
 * MetadataKeys .
 *
 * <p>
 * RAG metadata key 常量，写入 Document.metadata、构造 filterExpression 和返回引用来源时统一复用
 *
 * <p>
 * 字段说明：metadata key 必须全链路统一
 * 写入时叫 tenantId，检索时 filterExpression 也必须叫 tenantId
 * Redis 还需要在 RedisVectorStoreConfig 中声明 metadataFields，否则字段即使写入也不能过滤
 * Qdrant / Milvus 不需要 Redis MetadataField 声明，但 key 仍然必须一致
 *
 * <p>
 * key 分层
 * tenantId / knowledgeBaseId 是权限和知识库边界
 * documentId / documentType 是文档级过滤
 * chunkId / chunkIndex / contentHash 是 chunk 级定位和幂等辅助信息
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
public final class MetadataKeys {

    /**
     * 租户 ID，用于多租户数据隔离和默认检索过滤
     */
    public static final String TENANT_ID = "tenantId";

    /**
     * 知识库 ID，用于限定一次 RAG 查询只能检索指定知识库
     */
    public static final String KNOWLEDGE_BASE_ID = "knowledgeBaseId";

    /**
     * 文档 ID，用于同一文档重复索引时删除旧 chunk，也用于按文档收窄检索
     */
    public static final String DOCUMENT_ID = "documentId";

    /**
     * 文档类型，用于按 markdown、json、pdf、docx 等类型过滤检索范围
     */
    public static final String DOCUMENT_TYPE = "documentType";

    /**
     * 文档来源，用于返回引用来源和定位对象存储 objectKey 或业务来源路径
     */
    public static final String SOURCE = "source";

    /**
     * 文档索引时间，使用 epoch millis，便于按时间范围过滤
     */
    public static final String CREATED_AT = "createdAt";

    /**
     * chunk ID，当前使用 documentId + chunkIndex 构造，用于定位单个检索片段
     */
    public static final String CHUNK_ID = "chunkId";

    /**
     * chunk 序号，用于还原文档片段顺序和前端定位
     */
    public static final String CHUNK_INDEX = "chunkIndex";

    /**
     * chunk 内容 hash，用于判断片段内容是否变化，后续可扩展增量索引
     */
    public static final String CONTENT_HASH = "contentHash";

    private MetadataKeys() {
    }
}
