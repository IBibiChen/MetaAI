package com.metax.tool.argument;

import com.metax.tool.catalog.MetaToolNames;
import com.metax.tool.method.declarative.DeclarativeDateTimeTools;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.augment.AugmentedArgumentEvent;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ToolArgumentSupportTest .
 *
 * <p>
 * 工具参数增强工厂单元测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
class ToolArgumentSupportTest {

    @Test
    void auditedMethodToolProviderShouldAddAuditFieldsToInputSchema() {
        ToolCallback callback = convertedDeclarativeCallback(new AtomicReference<>());

        assertThat(callback.getToolDefinition().inputSchema()).contains("requestId");
        assertThat(callback.getToolDefinition().inputSchema()).contains("operatorId");
        assertThat(callback.getToolDefinition().inputSchema()).contains("zoneId");
    }

    @Test
    void auditedMethodToolProviderShouldConsumeAndRemoveAugmentedArguments() {
        AtomicReference<AugmentedArgumentEvent<ToolAuditArguments>> eventReference = new AtomicReference<>();
        ToolCallback callback = convertedDeclarativeCallback(eventReference);

        String result = callback.call("""
                {"zoneId":"UTC","requestId":"req-001","operatorId":"user-001"}
                """);

        AugmentedArgumentEvent<ToolAuditArguments> event = eventReference.get();
        assertThat(event).isNotNull();
        assertThat(event.arguments().requestId()).isEqualTo("req-001");
        assertThat(event.arguments().operatorId()).isEqualTo("user-001");
        assertThat(event.rawInput()).contains("zoneId");
        assertThat(OffsetDateTime.parse(result)).isNotNull();
    }

    private ToolCallback convertedDeclarativeCallback(
            AtomicReference<AugmentedArgumentEvent<ToolAuditArguments>> eventReference) {
        ToolCallback[] callbacks = ToolArgumentSupport
                .auditedMethodToolProvider(new DeclarativeDateTimeTools(), eventReference::set)
                .getToolCallbacks();
        return Arrays.stream(callbacks)
                .filter(callback -> MetaToolNames.METHOD_CONVERTED
                        .equals(callback.getToolDefinition().name()))
                .findFirst()
                .orElseThrow();
    }
}
