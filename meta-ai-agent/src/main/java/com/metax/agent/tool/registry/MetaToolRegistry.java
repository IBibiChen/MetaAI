package com.metax.agent.tool.registry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MetaToolRegistry .
 *
 * <p>
 * 请求级工具注册表，负责从 Spring AI ToolCallbackProvider 中解析允许本轮使用的工具
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
@Slf4j
public class MetaToolRegistry {

    private final Map<String, ToolCallback> callbacks;

    private final Set<String> allowedToolNames;

    /**
     * 构造请求级工具注册表
     *
     * @param toolCallbackProvider 请求级工具回调提供器
     * @param allowedToolNames     请求级工具 allowlist
     */
    public MetaToolRegistry(ToolCallbackProvider toolCallbackProvider, Set<String> allowedToolNames) {
        Assert.notNull(toolCallbackProvider, "工具回调提供器不能为空");
        Assert.notEmpty(allowedToolNames, "请求级工具 allowlist 不能为空");
        this.callbacks = callbacks(toolCallbackProvider);
        this.allowedToolNames = Set.copyOf(allowedToolNames);
        log.info("请求级工具注册表初始化完成：allowedToolNames = {}", this.allowedToolNames);
    }

    /**
     * 解析本轮请求允许暴露的工具
     *
     * <p>
     * requestedToolNames 为空时使用服务端 allowlist 中的全部工具
     * requestedToolNames 非空时必须全部命中 allowlist，避免客户端任意暴露未授权工具
     *
     * @param requestedToolNames 客户端请求的工具名称
     * @return 本轮可用的工具回调
     */
    public List<ToolCallback> resolve(List<String> requestedToolNames) {
        List<String> names = requestedToolNames == null || requestedToolNames.isEmpty()
                ? allowedToolNames.stream().toList()
                : requestedToolNames;
        List<ToolCallback> resolvedCallbacks = new ArrayList<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                log.warn("请求级工具解析失败：toolNames 包含空白工具名，requestedToolNames = {}", requestedToolNames);
                throw new IllegalArgumentException("toolNames 不能包含空白工具名");
            }
            if (!allowedToolNames.contains(name)) {
                log.warn("请求级工具解析失败：工具未进入 allowlist，name = {}，allowedToolNames = {}",
                        name, allowedToolNames);
                throw new IllegalArgumentException("工具未进入请求级 allowlist：" + name);
            }
            ToolCallback callback = callbacks.get(name);
            if (callback == null) {
                log.error("请求级工具解析失败：工具未注册，name = {}，registeredToolNames = {}",
                        name, callbacks.keySet());
                throw new IllegalArgumentException("工具未注册：" + name);
            }
            resolvedCallbacks.add(callback);
        }
        log.debug("请求级工具解析完成：requestedToolNames = {}，resolvedToolNames = {}",
                requestedToolNames, names);
        return List.copyOf(resolvedCallbacks);
    }

    /**
     * 返回请求级工具 allowlist
     *
     * @return 请求级工具名称列表
     */
    public Set<String> allowedToolNames() {
        return allowedToolNames;
    }

    /**
     * 构建请求级工具回调表
     *
     * <p>
     * 从 ToolCallbackProvider 中读取当前可候选的 ToolCallback，并按 ToolDefinition.name 建立只读映射
     * 如果出现重复工具名，说明模型侧工具协议已经冲突，必须在启动期直接失败
     *
     * @param toolCallbackProvider 请求级工具回调提供器
     * @return 按工具名索引的只读工具回调表
     */
    private Map<String, ToolCallback> callbacks(ToolCallbackProvider toolCallbackProvider) {
        Map<String, ToolCallback> result = new LinkedHashMap<>();
        for (ToolCallback callback : toolCallbackProvider.getToolCallbacks()) {
            String name = callback.getToolDefinition().name();
            if (result.putIfAbsent(name, callback) != null) {
                log.error("请求级工具注册失败：存在重复工具名，name = {}", name);
                throw new IllegalStateException("存在重复工具名：" + name);
            }
            log.debug("注册请求级候选工具：name = {}", name);
        }
        return Map.copyOf(result);
    }
}
