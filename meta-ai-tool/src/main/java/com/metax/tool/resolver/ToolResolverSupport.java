package com.metax.tool.resolver;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;

import java.util.List;

/**
 * ToolResolverSupport .
 *
 * <p>
 * Spring AI 工具回调解析器工厂，演示 StaticToolCallbackResolver 和 DelegatingToolCallbackResolver 的官方用法
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
public final class ToolResolverSupport {

    private ToolResolverSupport() {
    }

    /**
     * 创建静态工具回调解析器
     *
     * <p>
     * StaticToolCallbackResolver 适合把已经构造好的 ToolCallback 注册成只读工具表
     * 调用侧通过工具名解析，未命中时返回 null
     *
     * @param toolCallbacks 静态工具回调列表
     * @return 静态工具回调解析器
     */
    public static ToolCallbackResolver staticResolver(List<ToolCallback> toolCallbacks) {
        return new StaticToolCallbackResolver(toolCallbacks);
    }

    /**
     * 创建委托工具回调解析器
     *
     * <p>
     * DelegatingToolCallbackResolver 按顺序访问多个 resolver
     * 当前一个 resolver 未命中时才继续访问后一个 resolver，适合组合静态注册表和 Spring Bean resolver
     *
     * @param resolvers 按优先级排序的工具回调解析器
     * @return 委托工具回调解析器
     */
    public static ToolCallbackResolver delegatingResolver(List<ToolCallbackResolver> resolvers) {
        return new DelegatingToolCallbackResolver(resolvers);
    }
}
