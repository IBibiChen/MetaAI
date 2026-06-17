package com.metax.tool.execution;

import com.metax.tool.context.MetaToolContextKeys;
import com.metax.tool.function.programmatic.DateTimeFunctionTools;
import com.metax.tool.function.programmatic.FunctionToolCallbackFactory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ToolCallingOptionsFactoryTest .
 *
 * <p>
 * 工具执行选项工厂单元测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
class ToolCallingOptionsFactoryTest {

    @Test
    void userControlledOptionsShouldDisableInternalToolExecution() {
        ToolCallback callback = callback();

        ToolCallingChatOptions options = factory().userControlledOptions(List.of(callback), context());

        assertThat(options.getInternalToolExecutionEnabled()).isFalse();
        assertThat(options.getToolCallbacks()).containsExactly(callback);
        assertThat(options.getToolContext()).containsEntry(MetaToolContextKeys.TENANT_ID, "tenantA");
    }

    @Test
    void frameworkControlledOptionsShouldKeepInternalToolExecutionDefault() {
        ToolCallback callback = callback();

        ToolCallingChatOptions options = factory().frameworkControlledOptions(List.of(callback), context());

        assertThat(options.getInternalToolExecutionEnabled()).isNull();
        assertThat(options.getToolCallbacks()).containsExactly(callback);
    }

    @Test
    void advisorControlledOptionsShouldKeepInternalToolExecutionDefaultBeforeAdvisorCopiesOptions() {
        ToolCallback callback = callback();

        ToolCallingChatOptions options = factory().advisorControlledOptions(List.of(callback), context());

        assertThat(options.getInternalToolExecutionEnabled()).isNull();
        assertThat(options.getToolCallbacks()).containsExactly(callback);
    }

    @Test
    void namedToolOptionsShouldCarryToolNames() {
        ToolCallingChatOptions options = factory().namedToolOptions(Set.of("currentDateTime"), context());

        assertThat(options.getToolNames()).containsExactly("currentDateTime");
        assertThat(options.getToolContext()).containsEntry(MetaToolContextKeys.USER_ID, "userA");
    }

    private ToolCallingOptionsFactory factory() {
        return new ToolCallingOptionsFactory();
    }

    private ToolCallback callback() {
        return new FunctionToolCallbackFactory().zonedDateTimeCallback(new DateTimeFunctionTools());
    }

    private Map<String, Object> context() {
        return Map.of(
                MetaToolContextKeys.TENANT_ID, "tenantA",
                MetaToolContextKeys.USER_ID, "userA"
        );
    }
}
