package com.metax.tool.execution;

import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ToolCallingOptionsFactory .
 *
 * <p>
 * 工具执行选项工厂，演示 ToolCallingChatOptions 在不同工具执行路径下的关键配置
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
public class ToolCallingOptionsFactory {

    /**
     * 构造调用方手动执行工具的 ChatModel 选项
     *
     * <p>
     * internalToolExecutionEnabled 为 false 时，ChatModel 只返回工具调用请求，不负责执行工具
     * 该路径适合需要由业务代码手动审计、审批、限流或编排工具执行的底层场景
     *
     * @param callbacks   本轮注册给模型的工具回调
     * @param toolContext 工具执行上下文
     * @return 调用方手动执行工具的选项
     */
    public ToolCallingChatOptions userControlledOptions(List<ToolCallback> callbacks,
                                                        Map<String, Object> toolContext) {
        return ToolCallingChatOptions.builder()
                .toolCallbacks(callbacks)
                .toolContext(toolContext)
                .internalToolExecutionEnabled(false)
                .build();
    }

    /**
     * 构造按工具名解析的 ChatModel 选项
     *
     * <p>
     * toolNames 适合配合 ToolCallbackResolver 使用，模型侧只拿到这些名称对应的工具定义
     * 该路径通常用于 Spring Bean Function、静态工具表或插件式工具解析
     *
     * @param toolNames   本轮允许解析的工具名称
     * @param toolContext 工具执行上下文
     * @return 按工具名解析的工具调用选项
     */
    public ToolCallingChatOptions namedToolOptions(Set<String> toolNames,
                                                   Map<String, Object> toolContext) {
        return ToolCallingChatOptions.builder()
                .toolNames(toolNames)
                .toolContext(toolContext)
                .build();
    }

    /**
     * 构造 Framework-Controlled Tool Execution 路径的选项
     *
     * <p>
     * 不显式设置 internalToolExecutionEnabled 时，Spring AI 默认允许内部工具执行
     * 该路径适合简单工具调用场景，由框架内部完成工具调用循环
     *
     * @param callbacks   本轮注册给模型的工具回调
     * @param toolContext 工具执行上下文
     * @return 框架控制工具执行的选项
     */
    public ToolCallingChatOptions frameworkControlledOptions(List<ToolCallback> callbacks,
                                                             Map<String, Object> toolContext) {
        return toolCallbacksWithDefaultInternalExecution(callbacks, toolContext);
    }

    /**
     * 构造 Advisor-Controlled Tool Execution 路径的选项
     *
     * <p>
     * ToolCallAdvisor 会复制该选项并关闭内部工具执行，由 Advisor 自己接管工具调用循环
     * 该方法和 frameworkControlledOptions 的底层选项一致，区别在于调用侧会额外绑定 ToolCallAdvisor
     *
     * @param callbacks   本轮注册给模型的工具回调
     * @param toolContext 工具执行上下文
     * @return Advisor 控制工具执行的选项
     */
    public ToolCallingChatOptions advisorControlledOptions(List<ToolCallback> callbacks,
                                                           Map<String, Object> toolContext) {
        return toolCallbacksWithDefaultInternalExecution(callbacks, toolContext);
    }

    /**
     * 构造默认内部执行配置的 ToolCallback 选项
     *
     * @param callbacks   本轮注册给模型的工具回调
     * @param toolContext 工具执行上下文
     * @return 默认内部执行配置的工具调用选项
     */
    private ToolCallingChatOptions toolCallbacksWithDefaultInternalExecution(List<ToolCallback> callbacks,
                                                                             Map<String, Object> toolContext) {
        return ToolCallingChatOptions.builder()
                .toolCallbacks(callbacks)
                .toolContext(toolContext)
                .build();
    }
}
