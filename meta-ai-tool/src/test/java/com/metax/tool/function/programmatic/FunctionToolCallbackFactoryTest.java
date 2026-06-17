package com.metax.tool.function.programmatic;

import com.metax.tool.catalog.MetaToolNames;
import com.metax.tool.context.MetaToolContextKeys;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FunctionToolCallbackFactoryTest .
 *
 * <p>
 * 编程式 FunctionToolCallback 工厂单元测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
class FunctionToolCallbackFactoryTest {

    @Test
    void zonedDateTimeCallbackShouldExposeFunctionToolDefinition() {
        ToolCallback callback = factory().zonedDateTimeCallback(new DateTimeFunctionTools());

        assertThat(callback.getToolDefinition().name())
                .isEqualTo(MetaToolNames.FUNCTION_CALLBACK);
        assertThat(callback.getToolDefinition().description()).contains("FunctionToolCallback");
        assertThat(callback.getToolDefinition().inputSchema()).contains("zoneId");
        assertThat(callback.getToolMetadata().returnDirect()).isFalse();
    }

    @Test
    void zonedDateTimeCallbackShouldInvokeFunction() {
        ToolCallback callback = factory().zonedDateTimeCallback(new DateTimeFunctionTools());

        String result = callback.call("{\"zoneId\":\"UTC\"}");

        assertThat(result).isNotBlank();
        assertThat(OffsetDateTime.parse(result)).isNotNull();
    }

    @Test
    void supplierCallbackShouldUseVoidInputSchema() {
        ToolCallback callback = factory().currentUtcDateTimeSupplierCallback(new DateTimeFunctionTools());

        assertThat(callback.getToolDefinition().name())
                .isEqualTo(MetaToolNames.FUNCTION_SUPPLIER);
        assertThat(callback.getToolDefinition().inputSchema()).contains("object");
    }

    @Test
    void biFunctionCallbackShouldReadToolContext() {
        ToolCallback callback = factory().contextualDateTimeCallback(new DateTimeFunctionTools());
        ToolContext context = new ToolContext(Map.of(
                MetaToolContextKeys.TENANT_ID, "tenantA",
                MetaToolContextKeys.USER_ID, "userA"
        ));

        String result = callback.call("{\"zoneId\":\"UTC\"}", context);

        assertThat(result).contains("tenantId = tenantA");
        assertThat(result).contains("userId = userA");
    }

    private FunctionToolCallbackFactory factory() {
        return new FunctionToolCallbackFactory();
    }
}
