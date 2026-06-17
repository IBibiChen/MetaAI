package com.metax.tool.reference;

/**
 * ToolReferencePath .
 *
 * <p>
 * Spring AI 官方工具调用参考路径，用于区分方法工具、函数工具和工具定义查看入口
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
public enum ToolReferencePath {

    /**
     * Tool 对象通过 ChatClient.tools 在单次请求暴露
     */
    METHOD_DECLARATIVE_RUNTIME_TOOLS,

    /**
     * Tool 对象通过 ToolCallbacks.from 转为 ToolCallback 后在单次请求暴露
     */
    METHOD_DECLARATIVE_RUNTIME_CALLBACKS,

    /**
     * MethodToolCallback 在单次请求暴露
     */
    METHOD_PROGRAMMATIC_RUNTIME_CALLBACKS,

    /**
     * MethodToolCallback 通过 defaultToolCallbacks 在专用 ChatClient 全局暴露
     */
    METHOD_PROGRAMMATIC_DEFAULT_CALLBACKS,

    /**
     * FunctionToolCallback 在单次请求暴露
     */
    FUNCTION_PROGRAMMATIC_RUNTIME_CALLBACKS,

    /**
     * FunctionToolCallback 通过 defaultToolCallbacks 在专用 ChatClient 全局暴露
     */
    FUNCTION_PROGRAMMATIC_DEFAULT_CALLBACKS,

    /**
     * Spring Bean 函数通过 SpringBeanToolCallbackResolver 解析后暴露
     */
    FUNCTION_BEAN_RESOLVER_CALLBACKS,

    /**
     * Tool Specification 契约查看
     */
    SPECIFICATION_DEFINITIONS,

    /**
     * Tool Execution 执行选项查看
     */
    EXECUTION_OPTIONS,

    /**
     * 只查看工具定义，不调用模型
     */
    DEFINITIONS_ONLY
}
