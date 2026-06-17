package com.metax.tool.specification.conversion;

import org.springframework.ai.tool.execution.ToolCallResultConverter;

/**
 * ToolResultConversionSupport .
 *
 * <p>
 * 工具结果转换参考支持类，集中演示 ToolCallResultConverter 的官方扩展点
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
public final class ToolResultConversionSupport {

    private ToolResultConversionSupport() {
    }

    /**
     * 纯文本工具结果转换器
     *
     * <p>
     * ToolCallResultConverter 负责把工具返回值转换成可以交还给模型的字符串
     *
     * @return 纯文本结果转换器
     */
    public static ToolCallResultConverter plainTextResultConverter() {
        return new CustomToolCallResultConverter();
    }
}
