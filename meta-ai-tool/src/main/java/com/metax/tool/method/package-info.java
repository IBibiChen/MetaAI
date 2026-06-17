/**
 * Methods as Tools 官方路径
 *
 * <p>
 * 方法工具适合把已有 Java 对象上的方法暴露给模型
 * 声明式路径使用 @Tool 和 @ToolParam，代码短、语义直接，适合稳定的手写工具
 * 编程式路径使用 MethodToolCallback，适合动态工具、框架生成工具或需要显式控制 ToolDefinition 与 ToolMetadata 的场景
 *
 * <p>
 * 本包只放方法工具的通用示例和工厂
 * 具体业务方法工具应放在业务模块，并通过请求级 allowlist 显式暴露
 */
package com.metax.tool.method;
