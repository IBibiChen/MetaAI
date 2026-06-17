package com.metax.tool.method.declarative;

import com.metax.tool.catalog.MetaToolNames;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DeclarativeDateTimeToolsTest .
 *
 * <p>
 * 声明式方法工具单元测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
class DeclarativeDateTimeToolsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void toolCallbacksFromShouldResolveToolAnnotatedMethods() {
        ToolCallback[] callbacks = ToolCallbacks.from(new DeclarativeDateTimeTools());

        Map<String, ToolCallback> callbackMap = Arrays.stream(callbacks)
                .collect(Collectors.toMap(callback -> callback.getToolDefinition().name(), Function.identity()));

        assertThat(callbackMap).containsKeys(
                MetaToolNames.METHOD_NOW,
                MetaToolNames.METHOD_ZONED,
                MetaToolNames.METHOD_OPTIONAL_ZONE,
                MetaToolNames.METHOD_CONVERTED
        );
    }

    @Test
    void toolParamShouldAppearInInputSchema() {
        ToolCallback[] callbacks = ToolCallbacks.from(new DeclarativeDateTimeTools());

        ToolCallback callback = Arrays.stream(callbacks)
                .filter(item -> MetaToolNames.METHOD_ZONED
                        .equals(item.getToolDefinition().name()))
                .findFirst()
                .orElseThrow();

        assertThat(callback.getToolDefinition().inputSchema()).contains("zoneId");
        assertThat(callback.getToolDefinition().inputSchema()).contains("IANA 时区 ID");
    }

    @Test
    void optionalToolParamShouldNotAppearInRequiredSchema() throws Exception {
        ToolCallback[] callbacks = ToolCallbacks.from(new DeclarativeDateTimeTools());

        ToolCallback callback = Arrays.stream(callbacks)
                .filter(item -> MetaToolNames.METHOD_OPTIONAL_ZONE
                        .equals(item.getToolDefinition().name()))
                .findFirst()
                .orElseThrow();

        JsonNode schema = objectMapper.readTree(callback.getToolDefinition().inputSchema());
        JsonNode required = schema.path("required");
        assertThat(required.isMissingNode() || !required.toString().contains("zoneId")).isTrue();
        assertThat(callback.getToolDefinition().inputSchema()).contains("可选 IANA 时区 ID");
    }

    @Test
    void toolResultConverterShouldKeepDeclarativeMethodResultAsPlainText() {
        ToolCallback[] callbacks = ToolCallbacks.from(new DeclarativeDateTimeTools());

        ToolCallback callback = Arrays.stream(callbacks)
                .filter(item -> MetaToolNames.METHOD_CONVERTED
                        .equals(item.getToolDefinition().name()))
                .findFirst()
                .orElseThrow();

        String result = callback.call("{\"zoneId\":\"UTC\"}");

        assertThat(result).isNotBlank();
        assertThat(result).doesNotStartWith("\"");
        assertThat(OffsetDateTime.parse(result)).isNotNull();
    }
}
