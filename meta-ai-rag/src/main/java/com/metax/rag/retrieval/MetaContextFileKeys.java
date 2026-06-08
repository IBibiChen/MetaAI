package com.metax.rag.retrieval;

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
     * 本轮新上传的文件（当前请求刚接收到的文件）
     */
    public static final String INCOMING_FILES = "metax.contextFile.incomingFiles";

    /**
     * 本次参与上下文增强的所有文件
     */
    public static final String CONTEXT_FILES = "metax.contextFile.contextFiles";

    /**
     * 本次命中的文件 chunk
     */
    public static final String DOCUMENTS = "metax.contextFile.documents";

    /**
     * 是否跳过文件上下文
     */
    public static final String SKIP = "metax.contextFile.skip";

    private MetaContextFileKeys() {
    }
}
