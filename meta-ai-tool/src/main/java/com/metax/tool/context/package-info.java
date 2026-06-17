/**
 * ToolContext 执行期上下文
 *
 * <p>
 * ToolContext 用于把租户、用户、会话、审计等执行期信息传给工具
 * 这些信息不会作为工具参数 schema 发送给模型，因此适合承载不应由模型生成的安全边界
 *
 * <p>
 * 工具实现应优先从 ToolContext 读取权限和隔离信息
 * 不应让模型通过普通工具参数伪造 tenantId、userId 或 chatId
 *
 * <p>
 * MetaToolContextKeys 统一维护上下文字段名
 * ToolContextAccessor 统一封装 ToolContext 读取方式
 */
package com.metax.tool.context;
