package com.metax.agent.tool.registry;

import com.metax.tool.catalog.MetaToolNames;
import com.metax.tool.foundation.DateTimeTools;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MetaToolRegistryTest .
 *
 * <p>
 * 请求级工具注册表单元测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
class MetaToolRegistryTest {

    @Test
    void resolveShouldUseAllowlistWhenRequestedToolNamesIsEmpty() {
        MetaToolRegistry registry = registry();

        List<ToolCallback> callbacks = registry.resolve(List.of());

        assertThat(callbacks).hasSize(1);
        assertThat(callbacks.get(0).getToolDefinition().name()).isEqualTo(MetaToolNames.CURRENT_DATE_TIME);
    }

    @Test
    void resolveShouldRejectToolNameOutsideAllowlist() {
        MetaToolRegistry registry = registry();

        assertThatThrownBy(() -> registry.resolve(List.of("deleteFile")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("工具未进入请求级 allowlist");
    }

    private MetaToolRegistry registry() {
        ToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                .toolObjects(new DateTimeTools())
                .build();
        return new MetaToolRegistry(provider, Set.of(MetaToolNames.CURRENT_DATE_TIME));
    }
}
