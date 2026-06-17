package com.metax.tool.specification;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * ToolCallbackDefinitionView .
 *
 * <p>
 * 面向参考接口的工具定义视图，用于观察模型实际收到的工具名称、描述、入参 schema 和执行元数据
 *
 * @param name         工具名称
 * @param description  工具描述
 * @param inputSchema  工具入参 JSON Schema
 * @param returnDirect 是否直接返回工具结果
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
public record ToolCallbackDefinitionView(
        String name,
        String description,
        String inputSchema,
        boolean returnDirect
) {

    /**
     * 从 ToolCallback 提取参考视图
     *
     * @param callback Spring AI 工具回调
     * @return 工具定义视图
     */
    public static ToolCallbackDefinitionView from(ToolCallback callback) {
        ToolDefinition definition = callback.getToolDefinition();
        return new ToolCallbackDefinitionView(definition.name(), definition.description(), definition.inputSchema(),
                callback.getToolMetadata().returnDirect());
    }
}
