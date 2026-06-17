/**
 * 工具调用参考接口模型包
 *
 * <p>
 * 本包承载面向参考接口返回的路径枚举和响应模型
 * 它不表示 Spring AI 官方工具抽象，而是为了让 agent 参考接口能够清楚展示当前演示的是哪条官方路径
 * 不要把本包理解成工具运行时基础设施
 * 真正的工具运行时仍然由 ToolCallback、ToolCallAdvisor、ToolCallingChatOptions 和 ToolCallbackResolver 承担
 *
 * <p>
 * ToolReferencePath 用于标识 Methods as Tools、Functions as Tools、Tool Specification 和 Tool Execution 等演示入口
 * ToolReferenceResponse 用于返回模型回答、工具名称、工具定义视图和实现要点
 */
package com.metax.tool.reference;
