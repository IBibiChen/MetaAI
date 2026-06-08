package com.metax.chat.support;

/**
 * ChatDefaults .
 *
 * <p>
 * 聊天接口默认值
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
public final class ChatDefaults {

    /**
     * 默认会话 ID
     */
    public static final String CHAT_ID = "tenantId:userId:sessionId";

    /**
     * 默认普通对话消息
     */
    public static final String CHAT_MESSAGE = "你是谁";

    /**
     * 默认文件对话消息
     */
    public static final String FILE_CHAT_MESSAGE = "总结一下这个文件";

    /**
     * 默认知识库文件对话消息
     */
    public static final String RETRIEVAL_FILE_CHAT_MESSAGE = "对比知识库方案和上传文件";

    /**
     * 默认文档可见性
     */
    public static final String VISIBILITY = "PUBLIC";

    private ChatDefaults() {
    }
}
