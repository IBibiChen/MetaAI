package com.metax.history;

/**
 * ChatHistoryRole .
 *
 * <p>
 * 用于区分历史消息发送方
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/2
 */
public enum ChatHistoryRole {

    /**
     * 用户消息
     */
    USER("user"),

    /**
     * 模型回答
     */
    ASSISTANT("assistant");

    private final String value;

    ChatHistoryRole(String value) {
        this.value = value;
    }

    /**
     * 历史归档存储值
     *
     * @return 存储值
     */
    public String value() {
        return value;
    }
}
