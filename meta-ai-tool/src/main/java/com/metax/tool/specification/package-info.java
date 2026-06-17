/**
 * Tool Specification 工具规格参考包
 *
 * <p>
 * 本包对应 Spring AI 官方文档中的 Tool Specification 章节
 * 这里关注的是模型可见工具规格，而不是 Java 实现细节
 *
 * <p>
 * ToolDefinition 是模型真正看到的工具说明
 * 它包含 name、description 和 inputSchema
 * 模型不会看到 Java 方法签名、record 类型、Function 对象或业务类结构
 *
 * <p>
 * ToolMetadata 是工具执行元数据
 * 当前重点演示 returnDirect
 * returnDirect 为 true 时，工具结果会直接返回调用方，不再交回模型继续生成回答
 *
 * <p>
 * ToolSpecificationSupport 当前提供两个教学入口
 * returnDirectDateTimeCallback 演示如何构造直接返回结果的工具契约
 * validateUniqueToolNames 演示工具进入模型前如何校验名称唯一性
 *
 * <p>
 * 本包只放工具契约层的通用参考代码
 * 具体业务工具不应放在这里，应放在 agent 或对应业务模块
 */
package com.metax.tool.specification;
