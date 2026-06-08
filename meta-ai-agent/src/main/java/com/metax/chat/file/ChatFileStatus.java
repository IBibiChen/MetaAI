package com.metax.chat.file;

/**
 * ChatFileStatus .
 *
 * <p>
 * 聊天文件解析和临时索引状态
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
public enum ChatFileStatus {

    /**
     * 已上传，等待解析
     */
    UPLOADED,

    /**
     * 解析和临时索引中
     */
    PARSING,

    /**
     * 已解析并写入会话级临时索引
     */
    READY,

    /**
     * 解析失败
     */
    PARSE_FAILED
}
