package com.metax.tool.specification.conversion;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.execution.ToolCallResultConverter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ToolResultConversionSupportTest .
 *
 * <p>
 * 工具结果转换参考支持类单元测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
class ToolResultConversionSupportTest {

    @Test
    void plainTextResultConverterShouldReturnStringValue() {
        ToolCallResultConverter converter = ToolResultConversionSupport.plainTextResultConverter();

        String result = converter.convert("Asia/Shanghai", String.class);

        assertThat(result).isEqualTo("Asia/Shanghai");
    }

    @Test
    void plainTextResultConverterShouldReturnEmptyStringForNull() {
        ToolCallResultConverter converter = ToolResultConversionSupport.plainTextResultConverter();

        String result = converter.convert(null, String.class);

        assertThat(result).isEmpty();
    }

    @Test
    void plainTextResultConverterShouldReturnEmptyStringForVoidReturnType() {
        ToolCallResultConverter converter = ToolResultConversionSupport.plainTextResultConverter();

        String result = converter.convert("ignored", Void.TYPE);

        assertThat(result).isEmpty();
    }
}
