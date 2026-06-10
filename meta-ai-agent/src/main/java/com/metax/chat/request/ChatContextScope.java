package com.metax.chat.request;

/**
 * ChatContextScope .
 *
 * <p>
 * 聊天回答上下文范围，用于显式区分本轮回答只使用附件、只使用知识库，还是同时使用两者
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/10
 */
public enum ChatContextScope {

    /**
     * 只使用本轮显式选择的会话附件
     */
    FILES_ONLY,

    /**
     * 只使用知识库检索结果
     */
    KNOWLEDGE_ONLY,

    /**
     * 同时使用会话附件和知识库检索结果
     */
    FILES_AND_KNOWLEDGE
}
