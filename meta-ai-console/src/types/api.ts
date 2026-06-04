/**
 * 后端统一响应壳
 *
 * <p>
 * 后端 CommonResult<T> 对应的前端类型
 *
 * @example
 * {
 *   "code": 200,
 *   "message": "操作成功",
 *   "data": {},
 *   "timestamp": "2026-06-03T11:30:00"
 * }
 */
export interface CommonResult<T> {
    /** 响应码，200 表示业务成功 */
    code: number
    /** 响应消息，失败时用于页面提示 */
    message: string
    /** 实际业务数据 */
    data: T
    /** 后端响应时间 */
    timestamp: string
}

/**
 * MyBatis Plus 分页响应
 *
 * <p>
 * 后端 Page<T> 序列化后的常用字段
 */
export interface PageResult<T> {
    /** 当前页数据列表 */
    records: T[]
    /** 总记录数 */
    total: number
    /** 每页数量 */
    size: number
    /** 当前页码，从 1 开始 */
    current: number
    /** 总页数 */
    pages: number
}

/**
 * 知识库文件元数据
 *
 * <p>
 * 对应后端 StorageDocumentDO
 */
export interface StorageDocument {
    /** 文档元数据主键 */
    id: number
    /** 租户 ID */
    tenantId: string
    /** 知识库 ID */
    knowledgeBaseId: string
    /** 文档可见性，PUBLIC / DEPT / USER */
    visibility: string
    /** 部门 ID，visibility = DEPT 时可能存在 */
    deptId?: string
    /** 用户 ID，visibility = USER 时可能存在 */
    userId?: string
    /** 业务文档 ID，下载和索引接口使用该字段 */
    documentId: string
    /** 原始文件名 */
    originalFilename: string
    /** 对象存储 bucket */
    bucket: string
    /** 对象存储 object key */
    objectKey: string
    /** 文件 MIME 类型 */
    contentType?: string
    /** 文件大小，单位为字节 */
    fileSize: number
    /** 文件 SHA-256 摘要 */
    fileSha256: string
    /** 文档类型，通常来自文件后缀或上传参数 */
    documentType?: string
    /** 来源标识，用于 RAG 引用来源展示 */
    source?: string
    /** 存储 provider，例如 object */
    storageProvider: string
    /** 对象存储 etag */
    storageEtag?: string
    /** 对象存储版本 ID */
    storageVersionId?: string
    /** 索引状态，PENDING / INDEXING / INDEXED / FAILED */
    indexStatus: string
    /** 已写入向量库的 chunk 数 */
    chunkCount: number
    /** 最新索引执行 ID */
    latestIndexingRunId?: string
    /** 是否启用 */
    enabled: boolean
    /** 是否删除 */
    deleted: boolean
    /** 创建时间 */
    createdAt: string
    /** 更新时间 */
    updatedAt: string
}

/**
 * 文件上传响应
 *
 * <p>
 * 对应后端 StorageDocumentUploadResponse
 */
export interface StorageDocumentUploadResponse {
    /** 业务文档 ID */
    documentId: string
    /** 原始文件名 */
    originalFilename: string
    /** 文档可见性 */
    visibility: string
    /** 部门 ID */
    deptId?: string
    /** 用户 ID */
    userId?: string
    /** 对象存储 bucket */
    bucket: string
    /** 对象存储 object key */
    objectKey: string
    /** 文件大小，单位为字节 */
    fileSize: number
    /** 文件 SHA-256 摘要 */
    fileSha256: string
    /** 文档类型 */
    documentType?: string
    /** 索引状态 */
    indexStatus: string
    /** 索引 chunk 数 */
    chunkCount: number
    /** 最新索引执行 ID */
    latestIndexingRunId?: string
}

/**
 * 文件分页查询参数
 *
 * @example
 * {
 *   tenantId: 't1',
 *   knowledgeBaseId: 'kb1',
 *   keyword: 'Spring AI',
 *   current: 1,
 *   size: 20
 * }
 */
export interface StorageDocumentQuery {
    /** 租户 ID，后端必填 */
    tenantId: string
    /** 知识库 ID，后端必填 */
    knowledgeBaseId: string
    /** 文档可见性筛选 */
    visibility?: string
    /** 部门 ID 筛选 */
    deptId?: string
    /** 用户 ID 筛选 */
    userId?: string
    /** 索引状态筛选 */
    indexStatus?: string
    /** 文件名关键字 */
    keyword?: string
    /** 当前页码，从 1 开始 */
    current?: number
    /** 每页数量 */
    size?: number
}

/**
 * 聊天历史消息
 *
 * <p>
 * 对应后端 ChatHistoryDO
 */
export interface ChatHistory {
    /** 历史消息主键 */
    id: string
    /** 会话主键 */
    chatId?: string
    /** 会话 ID，建议格式 tenantId:userId:sessionId */
    conversationId: string
    /** 对话类型，CHAT / RAG / RAG_DETAILS */
    chatType: string
    /** 消息角色，USER / ASSISTANT */
    role: string
    /** 消息正文 */
    content: string
    /** RAG 引用来源 JSON */
    referencesJson?: string
    /** 消息创建时间 */
    createdAt: string
}

/**
 * 聊天会话
 *
 * <p>
 * 对应后端 meta_chat 会话主表
 */
export interface MetaChat {
    /** 会话主键 */
    id: string
    /** 租户 ID */
    tenantId: string
    /** 用户 ID */
    userId: string
    /** 会话 ID */
    conversationId: string
    /** 会话标题 */
    title: string
    /** 最后一条消息预览 */
    lastMessage?: string
    /** 最后一条消息角色 */
    lastRole?: string
    /** 会话模式 */
    chatMode: string
    /** 知识库 ID */
    knowledgeBaseId?: string
    /** 消息数量 */
    messageCount: number
    /** 是否置顶 */
    pinned: boolean
    /** 是否收藏 */
    favorite: boolean
    /** 是否归档 */
    archived: boolean
    /** 是否软删除 */
    deleted: boolean
    /** 最后一条消息时间 */
    lastMessageAt: string
    /** 创建时间 */
    createdAt: string
    /** 更新时间 */
    updatedAt: string
}

/**
 * RAG 引用来源
 *
 * <p>
 * 用于展示模型回答引用了哪些知识库片段
 */
export interface RetrievalReference {
    /** 命中的文本片段 */
    text?: string
    /** 相似度分数或重排分数 */
    score?: number
    /** 文档 metadata，例如 source、filename、documentId */
    metadata?: Record<string, unknown>
    /** 后端组装的下载地址 */
    downloadUrl?: string
}

/**
 * RAG 文件引用
 *
 * <p>
 * 普通 /v1/rag 只返回聊天窗口需要展示和下载的文件信息
 */
export interface RetrievalCitation {
    /** 原始文件名 */
    filename: string
    /** 文档 ID */
    documentId: string
}

/**
 * RAG 检索链路 Trace
 *
 * <p>
 * 用于排查 query 改写、过滤条件、召回数量和阶段耗时
 */
export interface RetrievalTrace {
    /** 用户原始问题 */
    originalQuery?: string
    /** 改写后的检索 query */
    transformedQuery?: string
    /** 实际过滤表达式 */
    filter?: string
    /** 本次检索 topK */
    topK?: number
    /** 本次相似度阈值 */
    similarityThreshold?: number
    /** 向量库原始召回数量 */
    retrievedCount?: number
    /** 进入上下文的文档数量 */
    contextCount?: number
    /** 各阶段补充信息 */
    stages?: Record<string, unknown>
}

/**
 * RAG 聊天响应
 */
export interface RetrievalChatResponse {
    /** 模型最终回答 */
    answer?: string
    /** 后端解析后的会话 ID */
    conversationId: string
    /** 引用来源列表 */
    references?: RetrievalCitation[]
    /** 检索链路 Trace */
    trace?: RetrievalTrace
}

/**
 * 流式对话 meta 事件
 */
export interface ChatStreamMeta {
    /** 后端解析后的会话 ID */
    conversationId: string
}

/**
 * 流式对话 delta 事件
 */
export interface ChatStreamDelta {
    /** 模型增量文本 */
    content: string
}

/**
 * 流式对话 done 事件
 */
export interface ChatStreamDone {
    /** 模型最终回答 */
    answer?: string
    /** 后端解析后的会话 ID */
    conversationId: string
    /** RAG 引用来源列表 */
    references?: RetrievalCitation[]
}

/**
 * 流式对话 error 事件
 */
export interface ChatStreamError {
    /** 错误消息 */
    message: string
}

export type ChatStreamEventName = 'meta' | 'delta' | 'done' | 'error'

/**
 * 聊天请求参数
 *
 * <p>
 * 普通聊天只使用 conversationId 和 msg
 * RAG 聊天会额外使用 tenantId、knowledgeBaseId 和可选检索范围
 */
export interface ChatOptions {
    /** 会话 ID */
    conversationId: string
    /** 用户消息 */
    msg: string
    /** 租户 ID */
    tenantId: string
    /** 用户 ID */
    userId: string
    /** 知识库 ID */
    knowledgeBaseId: string
    /** 可选文档 ID，用于限制只检索某个文档 */
    documentId?: string
    /** 可选文档类型，例如 md、pdf、txt、json */
    documentType?: string
    /** 当前用户可访问部门 ID，多个值用英文逗号分隔 */
    deptIds?: string
}
