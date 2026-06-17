package com.metax.tool.context;

import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;
import java.util.Optional;

/**
 * ToolContextAccessor .
 *
 * <p>
 * ToolContext 读取辅助类，统一封装工具执行期上下文字段的安全读取方式
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
public final class ToolContextAccessor {

    private ToolContextAccessor() {
    }

    /**
     * 按 key 读取字符串上下文
     *
     * <p>
     * ToolContext 只在工具执行期可见，不会作为工具参数 schema 暴露给模型
     * 调用侧可以用它传递租户、用户、会话和审计边界
     *
     * @param toolContext Spring AI 工具执行上下文
     * @param key         上下文字段名
     * @return 字符串上下文值
     */
    public static Optional<String> stringValue(ToolContext toolContext, String key) {
        if (toolContext == null || key == null || key.isBlank()) {
            return Optional.empty();
        }
        Map<String, Object> values = toolContext.getContext();
        Object value = values.get(key);
        return value == null ? Optional.empty() : Optional.of(String.valueOf(value));
    }
}
