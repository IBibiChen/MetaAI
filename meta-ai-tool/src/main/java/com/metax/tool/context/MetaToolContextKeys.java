package com.metax.tool.context;

/**
 * MetaToolContextKeys .
 *
 * <p>
 * Spring AI ToolContext 上下文字段名，统一约束工具方法读取的租户、用户和会话边界
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
public final class MetaToolContextKeys {

    /**
     * 租户 ID
     */
    public static final String TENANT_ID = "tenantId";

    /**
     * 用户 ID
     */
    public static final String USER_ID = "userId";

    /**
     * 会话 ID
     */
    public static final String CHAT_ID = "chatId";

    private MetaToolContextKeys() {
    }
}
