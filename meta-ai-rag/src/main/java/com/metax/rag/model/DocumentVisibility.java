package com.metax.rag.model;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * DocumentVisibility .
 *
 * <p>
 * RAG 文档可见性，控制向量检索时的权限过滤范围
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
public enum DocumentVisibility {

    /**
     * 同租户同知识库内公共可见
     */
    PUBLIC,

    /**
     * 指定部门范围可见
     */
    DEPT,

    /**
     * 指定用户私有可见
     */
    USER;

    /**
     * 解析可见性，默认为 PUBLIC
     *
     * @param value 可见性字符串
     * @return 文档可见性
     */
    public static DocumentVisibility resolve(String value) {
        if (!StringUtils.hasText(value)) {
            return PUBLIC;
        }
        return DocumentVisibility.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
