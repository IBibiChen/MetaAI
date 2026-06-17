/**
 * Functions as Tools 官方路径
 *
 * <p>
 * 函数工具适合把 Java Function、Supplier、Consumer 或 BiFunction 暴露给模型
 * FunctionToolCallback 是该路径的核心实现，负责绑定函数、输入类型、工具定义、执行元数据和结果转换器
 *
 * <p>
 * Spring Bean 函数也可以通过 SpringBeanToolCallbackResolver 按 Bean 名解析成 ToolCallback
 * Bean 名就是工具名，@Description 或输入类型上的 JsonClassDescription 可以成为工具描述来源
 */
package com.metax.tool.function;
