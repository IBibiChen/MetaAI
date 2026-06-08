package com.metax.chat.history;

/**
 * MetaChatHistoryType .
 *
 * <p>
 * 用于区分普通对话、文件对话、RAG 对话和 RAG details 调试对话
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/2
 */
public enum MetaChatHistoryType {

    /**
     * 普通记忆对话
     */
    CHAT("chat"),

    /**
     * 文件上下文对话
     */
    FILE_CHAT("file_chat"),

    /**
     * RAG 检索增强对话
     */
    RAG("rag"),

    /**
     * RAG 检索增强详情对话
     */
    RAG_DETAILS("rag_details");

    private final String value;

    MetaChatHistoryType(String value) {
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
