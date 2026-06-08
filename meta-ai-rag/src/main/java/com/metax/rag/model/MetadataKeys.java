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
 * Redis 还需要在 MetaRedisVectorStoreConfig 中声明 metadataFields，否则字段即使写入也不能过滤
 * Qdrant / Milvus 不需要 Redis MetadataField 声明，但 key 仍然必须一致
 *
 * <p>
 * key 分层
 * scope 用于区分知识库文档和会话级文件上下文，避免临时文件污染普通 RAG 检索
 * tenantId / kbId 是租户和知识库边界
 * visibility / deptId / userId 是权限过滤边界
 * documentId / documentType 是知识库文档级收窄过滤
 * chatId / fileId 是会话级文件上下文过滤边界
 * chunkId / chunkIndex / contentHash 是 chunk 级定位和幂等辅助信息
 *
 * <p>
 * 语义边界
 * scope = knowledge 时使用 documentId 表示知识库文档 ID
 * scope = session 时使用 fileId 表示会话级临时文件 ID
 * documentId 和 fileId 不能互相冒充，响应层按不同场景做展示适配
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
public final class MetadataKeys {

    /**
     * 检索数据作用域，用于区分知识库文档和会话级文件上下文
     */
    public static final String SCOPE = "scope";

    /**
     * 知识库文档作用域
     */
    public static final String SCOPE_KNOWLEDGE = "knowledge";

    /**
     * 会话作用域，用于标识会话级文件上下文
     */
    public static final String SCOPE_SESSION = "session";

    /**
     * 租户 ID，用于多租户数据隔离和默认检索过滤
     */
    public static final String TENANT_ID = "tenantId";

    /**
     * 知识库 ID (KB = Knowledge Base)
     */
    public static final String KB_ID = "kbId";

    /**
     * 文档可见性，用于区分公共、部门和用户私有文档
     */
    public static final String VISIBILITY = "visibility";

    /**
     * 部门 ID，用于部门级权限过滤
     */
    public static final String DEPT_ID = "deptId";

    /**
     * 用户 ID，用于用户私有文档权限过滤
     */
    public static final String USER_ID = "userId";

    /**
     * 会话 ID，用于会话级文件上下文临时索引隔离
     */
    public static final String CHAT_ID = "chatId";

    /**
     * 会话级文件 ID，用于限定当前会话可检索的临时文件范围
     */
    public static final String FILE_ID = "fileId";

    /**
     * 知识库文档 ID，用于同一文档重复索引时删除旧 chunk，也用于按文档收窄检索
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
     * 知识库文档名称，用于前端展示知识库引用来源
     */
    public static final String DOCUMENT_NAME = "documentName";

    /**
     * 会话级文件名称，用于前端展示文件上下文来源
     */
    public static final String FILE_NAME = "fileName";

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
