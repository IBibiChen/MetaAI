package com.metax.rag.retrieval.advisor;

/**
 * MetaContextFileKeys .
 *
 * <p>
 * 会话级文件上下文 Advisor key，统一用于 ChatClient advisor context 和 ChatResponse metadata
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
public final class MetaContextFileKeys {

    /**
     * 租户 ID
     */
    public static final String TENANT_ID = "metax.contextFile.tenantId";

    /**
     * 用户 ID
     */
    public static final String USER_ID = "metax.contextFile.userId";

    /**
     * 会话 ID
     */
    public static final String CHAT_ID = "metax.contextFile.chatId";

    /**
     * 原始用户问题
     */
    public static final String ORIGINAL_USER_QUERY = "metax.contextFile.originalUserQuery";

    /**
     * 本次参与上下文增强的所有会话文件
     *
     * <p>
     * 由业务 Service 层解析 fileIds 后写入，Advisor 只消费该字段
     */
    public static final String CONTEXT_FILES = "metax.contextFile.contextFiles";

    /**
     * 本次命中的会话文件 chunk
     *
     * <p>
     * 由文件 Advisor 检索后写入，用于响应 metadata 和排查文件上下文命中情况
     */
    public static final String DOCUMENTS = "metax.contextFile.documents";

    /**
     * 是否跳过文件上下文
     */
    public static final String SKIP = "metax.contextFile.skip";

    private MetaContextFileKeys() {
    }
}
