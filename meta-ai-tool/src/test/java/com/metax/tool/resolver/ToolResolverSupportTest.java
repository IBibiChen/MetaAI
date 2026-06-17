package com.metax.tool.resolver;

import com.metax.tool.catalog.MetaToolNames;
import com.metax.tool.method.programmatic.ProgrammaticDateTimeTools;
import com.metax.tool.method.programmatic.ProgrammaticMethodToolCallbackFactory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ToolResolverSupportTest .
 *
 * <p>
 * 工具名称解析器工厂单元测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
class ToolResolverSupportTest {

    @Test
    void staticResolverShouldResolveToolCallbackByName() {
        ToolCallback callback = callback();
        ToolCallbackResolver resolver = ToolResolverSupport.staticResolver(List.of(callback));

        ToolCallback resolved = resolver.resolve(MetaToolNames.METHOD_CALLBACK_CONVERTED);

        assertThat(resolved).isSameAs(callback);
    }

    @Test
    void delegatingResolverShouldTryNextResolverWhenPreviousResolverMisses() {
        ToolCallback callback = callback();
        ToolCallbackResolver emptyResolver = ToolResolverSupport.staticResolver(List.of());
        ToolCallbackResolver staticResolver = ToolResolverSupport.staticResolver(List.of(callback));
        ToolCallbackResolver delegatingResolver = ToolResolverSupport.delegatingResolver(
                List.of(emptyResolver, staticResolver));

        ToolCallback resolved = delegatingResolver.resolve(MetaToolNames.METHOD_CALLBACK_CONVERTED);

        assertThat(resolved).isSameAs(callback);
        assertThat(delegatingResolver.resolve("missingTool")).isNull();
    }

    private ToolCallback callback() {
        return new ProgrammaticMethodToolCallbackFactory()
                .convertedCurrentDateTimeCallback(new ProgrammaticDateTimeTools());
    }
}
