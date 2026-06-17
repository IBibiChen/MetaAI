package com.metax.tool.catalog;

/**
 * MetaToolCategory .
 *
 * <p>
 * 工具风险分类，用于区分全局默认工具和请求级显式工具的准入边界
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
public enum MetaToolCategory {

    /**
     * 只读工具
     */
    READ_ONLY,

    /**
     * 有业务写入但风险可控的工具
     */
    WRITE_SAFE,

    /**
     * 高风险写入工具
     */
    WRITE_DANGEROUS
}
