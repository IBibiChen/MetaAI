package com.metax.tool.method.programmatic;

import com.metax.tool.catalog.MetaToolNames;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProgrammaticMethodToolCallbackFactoryTest .
 *
 * <p>
 * 编程式 MethodToolCallback 工厂单元测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
class ProgrammaticMethodToolCallbackFactoryTest {

    @Test
    void currentDateTimeCallbackShouldExposeProgrammaticToolDefinition() {
        ToolCallback callback = callback();

        assertThat(callback.getToolDefinition().name())
                .isEqualTo(MetaToolNames.METHOD_CALLBACK);
        assertThat(callback.getToolDefinition().description()).contains("编程式 MethodToolCallback");
        assertThat(callback.getToolDefinition().inputSchema()).contains("zoneId");
        assertThat(callback.getToolMetadata().returnDirect()).isFalse();
    }

    @Test
    void currentDateTimeCallbackShouldInvokePlainJavaMethod() {
        ToolCallback callback = callback();

        String result = callback.call("{\"zoneId\":\"UTC\"}");

        assertThat(result).isNotBlank();
        // MethodToolCallback 会把 String 返回值转换成 JSON 字符串字面量，直接调用 call 时需要去掉外层引号再按时间解析
        assertThat(OffsetDateTime.parse(jsonStringValue(result))).isNotNull();
    }

    @Test
    void convertedCurrentDateTimeCallbackShouldUsePlainTextResultConverter() {
        ToolCallback callback = new ProgrammaticMethodToolCallbackFactory()
                .convertedCurrentDateTimeCallback(new ProgrammaticDateTimeTools());

        String result = callback.call("{\"zoneId\":\"UTC\"}");

        assertThat(callback.getToolDefinition().name())
                .isEqualTo(MetaToolNames.METHOD_CALLBACK_CONVERTED);
        assertThat(result).doesNotStartWith("\"");
        assertThat(OffsetDateTime.parse(result)).isNotNull();
    }

    private ToolCallback callback() {
        return new ProgrammaticMethodToolCallbackFactory()
                .currentDateTimeCallback(new ProgrammaticDateTimeTools());
    }

    /**
     * 提取 JSON 字符串字面量中的实际文本
     *
     * @param value ToolCallback.call 返回值
     * @return 去掉外层引号后的字符串
     */
    private String jsonStringValue(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
