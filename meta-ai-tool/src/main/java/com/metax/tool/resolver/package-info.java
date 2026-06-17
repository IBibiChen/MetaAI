/**
 * 工具名称解析
 *
 * <p>
 * ToolCallbackResolver 负责把 toolName 解析成 ToolCallback
 * 该路径通常和 ToolCallingChatOptions.toolNames 配合使用，让调用侧只传工具名称，不直接传完整回调对象
 *
 * <p>
 * StaticToolCallbackResolver 适合静态工具表
 * DelegatingToolCallbackResolver 适合把多个解析器按优先级组合起来
 * SpringBeanToolCallbackResolver 适合按 Spring Bean 名解析 Function、Supplier、Consumer 或 BiFunction Bean
 *
 * <p>
 * meta-ai-tool 提供静态解析器和委托解析器的通用示例
 * Spring Bean 解析器需要 ApplicationContext，运行参考放在 agent 模块
 */
package com.metax.tool.resolver;
