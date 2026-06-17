/**
 * 编程式方法工具
 *
 * <p>
 * 编程式方法工具使用 MethodToolCallback 把普通 Java Method 包装成 ToolCallback
 * 该路径完整暴露 Method、ToolDefinition、ToolMetadata、工具对象和结果转换器之间的关系
 * MethodToolCallback.builder().toolCallResultConverter(...) 可以显式绑定自定义结果转换器
 * 这也是 @Tool(resultConverter = ...) 在编程式路径上的等价扩展点
 *
 * <p>
 * 编程式路径不要求方法带 @Tool 注解
 * 这意味着方法是否暴露给模型完全由外部工厂和调用侧决定，适合做工具注册表、插件系统和动态能力装配
 */
package com.metax.tool.method.programmatic;
