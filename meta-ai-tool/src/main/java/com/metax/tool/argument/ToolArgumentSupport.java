package com.metax.tool.argument;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.augment.AugmentedArgumentEvent;
import org.springframework.ai.tool.augment.AugmentedToolCallbackProvider;

import java.util.function.Consumer;

/**
 * ToolArgumentSupport .
 *
 * <p>
 * Spring AI 工具参数增强工厂，演示 AugmentedToolCallbackProvider 的官方用法
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
public final class ToolArgumentSupport {

    private ToolArgumentSupport() {
    }

    /**
     * 创建带审计参数增强的工具回调提供者
     *
     * <p>
     * AugmentedToolCallbackProvider 会把 ToolAuditArguments 的字段追加到原始工具 input schema
     * 工具调用时，argumentConsumer 可以观察这些增强参数，随后再按配置决定是否从原始工具入参中移除
     *
     * @param toolObject       带 @Tool 方法的工具对象
     * @param argumentConsumer 增强参数事件消费器
     * @return 带审计参数增强的工具回调提供者
     */
    public static ToolCallbackProvider auditedMethodToolProvider(Object toolObject,
                                                                 Consumer<AugmentedArgumentEvent<ToolAuditArguments>> argumentConsumer) {
        return AugmentedToolCallbackProvider.<ToolAuditArguments>builder()
                .toolObject(toolObject)
                .argumentType(ToolAuditArguments.class)
                .argumentConsumer(argumentConsumer)
                .removeExtraArgumentsAfterProcessing(true)
                .build();
    }
}
