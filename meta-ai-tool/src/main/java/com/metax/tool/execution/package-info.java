/**
 * Tool Execution 工具执行策略
 *
 * <p>
 * 本包对应 Spring AI 官方文档中的 Tool Execution 章节
 * ToolCallingOptionsFactory 演示 Framework-Controlled、Advisor-Controlled 和 User-Controlled 三类执行路径
 * ToolCallAdvisor 把工具调用循环接入 ChatClient advisor 链
 * ToolCallingManager 负责协调模型工具调用请求和工具执行结果
 * ToolExecutionExceptionProcessor 负责决定工具异常是反馈给模型还是抛给调用方
 * ToolCallingChatOptions 决定本轮注册哪些工具、传递哪些 ToolContext，以及是否由模型内部执行工具
 *
 * <p>
 * 对无副作用基础工具，可以把异常转成可读文本让模型继续回答
 * 对写操作、鉴权失败、资金或数据变更类工具，应倾向于抛出异常并由业务层处理
 * rethrowExceptions 适合让业务层继续识别明确的 RuntimeException 类型
 * ToolExceptionHandlingSupport 演示异常处理策略
 * 工具结果转换属于 Tool Specification 下的 Result Conversion，放在 specification.conversion 包
 *
 * <p>
 * 官方 Observability 章节不在本包单独建模
 * 工具调用日志、追踪和审计由 agent 侧 Advisor 链、参考接口和应用观测体系承接
 * 本包只沉淀工具执行选项和异常处理策略
 */
package com.metax.tool.execution;
