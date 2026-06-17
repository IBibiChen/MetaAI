/**
 * 工具参数增强
 *
 * <p>
 * Tool Argument Augmentation 用于在已有 ToolCallback 外层追加额外工具参数 schema
 * Spring AI 官方实现是 AugmentedToolCallbackProvider，它会包装已有 ToolCallbackProvider 或 @Tool 工具对象
 *
 * <p>
 * 增强参数来自一个 record 类型
 * record 字段会追加到工具 input schema，模型可以在工具调用参数中生成这些字段
 * Spring AI 当前 augmenter 重点处理字段追加，字段描述可能按默认描述生成
 * 工具执行前，AugmentedToolCallback 会把增强参数转换成 record 并发送给 argumentConsumer
 *
 * <p>
 * removeExtraArgumentsAfterProcessing 为 true 时，增强参数会在委托给原始工具前从 JSON 入参中移除
 * 这可以避免原始工具方法收到自己并不声明的参数
 *
 * <p>
 * 该机制不同于 ToolContext
 * ToolContext 是调用侧安全注入的执行期上下文，不进入模型可见 schema
 * Argument Augmentation 会改变工具 schema，因此更适合观察、补充模型可见参数或做教学演示
 * 租户、用户、权限这类不能被模型伪造的字段仍应优先使用 ToolContext
 */
package com.metax.tool.argument;
