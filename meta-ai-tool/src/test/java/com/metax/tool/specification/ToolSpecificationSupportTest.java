package com.metax.tool.specification;

import com.metax.tool.catalog.MetaToolNames;
import com.metax.tool.function.programmatic.DateTimeFunctionTools;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ToolSpecificationSupportTest .
 *
 * <p>
 * 工具契约参考支持类单元测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
class ToolSpecificationSupportTest {

    @Test
    void returnDirectCallbackShouldExposeToolMetadata() {
        ToolCallback callback = support().returnDirectDateTimeCallback(new DateTimeFunctionTools());

        assertThat(callback.getToolDefinition().name())
                .isEqualTo(MetaToolNames.SPEC_RETURN_DIRECT);
        assertThat(callback.getToolMetadata().returnDirect()).isTrue();
        assertThat(callback.getToolDefinition().inputSchema()).contains("zoneId");
    }

    @Test
    void validateUniqueToolNamesShouldRejectDuplicateNames() {
        ToolCallback callback = support().returnDirectDateTimeCallback(new DateTimeFunctionTools());

        assertThatThrownBy(() -> support().validateUniqueToolNames(List.of(callback, callback)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Multiple tools with the same name");
    }

    private ToolSpecificationSupport support() {
        return new ToolSpecificationSupport();
    }
}
