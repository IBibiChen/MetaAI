package com.metax.tool.execution;

import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;

import java.util.List;

/**
 * ToolExceptionHandlingSupport .
 *
 * <p>
 * 工具异常处理策略辅助类，集中演示 ToolExecutionExceptionProcessor 的官方扩展点
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
public final class ToolExceptionHandlingSupport {

    private ToolExceptionHandlingSupport() {
    }

    /**
     * 默认工具异常处理器
     *
     * <p>
     * alwaysThrow 为 false 时，工具异常会转换成字符串反馈给模型
     * 生产系统暴露写操作或敏感操作时应按业务风险改成 true 或显式重抛指定异常
     *
     * @return 默认工具异常处理器
     */
    public static ToolExecutionExceptionProcessor defaultExceptionProcessor() {
        return modelFeedbackExceptionProcessor();
    }

    /**
     * 将工具异常反馈给模型的处理器
     *
     * <p>
     * alwaysThrow 为 false 时，异常消息会作为工具执行结果交还模型
     * 该策略只适合无副作用、可恢复、允许模型继续组织回答的工具
     *
     * @return 将异常反馈给模型的处理器
     */
    public static ToolExecutionExceptionProcessor modelFeedbackExceptionProcessor() {
        return DefaultToolExecutionExceptionProcessor.builder()
                .alwaysThrow(false)
                .build();
    }

    /**
     * 将工具异常抛给调用方的处理器
     *
     * <p>
     * alwaysThrow 为 true 时，Spring AI 不会把异常转换成模型可见文本
     * 写操作、鉴权失败、资金或数据变更工具应优先选择该策略
     *
     * @return 直接抛出异常的处理器
     */
    public static ToolExecutionExceptionProcessor throwingExceptionProcessor() {
        return DefaultToolExecutionExceptionProcessor.builder()
                .alwaysThrow(true)
                .build();
    }

    /**
     * 按异常类型白名单重抛的处理器
     *
     * <p>
     * rethrowExceptions 会解包 ToolExecutionException 的 cause，并把匹配的 RuntimeException 原样抛出
     * 该策略适合让业务层继续识别 IllegalArgumentException、SecurityException 等明确异常类型
     *
     * @param exceptions 需要原样重抛的运行时异常类型
     * @return 按异常类型白名单重抛的处理器
     */
    public static ToolExecutionExceptionProcessor rethrowingExceptionProcessor(
            List<Class<? extends RuntimeException>> exceptions) {
        return DefaultToolExecutionExceptionProcessor.builder()
                .alwaysThrow(false)
                .rethrowExceptions(exceptions)
                .build();
    }
}
