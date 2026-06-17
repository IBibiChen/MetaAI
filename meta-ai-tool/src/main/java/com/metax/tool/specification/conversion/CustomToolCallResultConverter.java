package com.metax.tool.specification.conversion;

import org.springframework.ai.tool.execution.ToolCallResultConverter;
import org.springframework.lang.Nullable;

import java.lang.reflect.Type;

/**
 * CustomToolCallResultConverter .
 *
 * <p>
 * 自定义工具调用结果转换器，演示 Spring AI ToolCallResultConverter 的标准扩展方式
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
public class CustomToolCallResultConverter implements ToolCallResultConverter {

    /**
     * 将工具返回值转换为模型可读取的字符串
     *
     * <p>
     * Spring AI 会把工具方法或函数的返回值先交给该方法，再把转换后的字符串回传给模型
     * 本示例刻意保持纯文本语义，方便和官方 DefaultToolCallResultConverter 的 JSON 字符串行为对比
     *
     * @param result     工具原始返回值，可能为空
     * @param returnType 工具声明返回类型，可能为空
     * @return 模型可读取的工具执行结果
     */
    @Override
    public String convert(@Nullable Object result, @Nullable Type returnType) {
        if (returnType == Void.TYPE || returnType == Void.class || result == null) {
            return "";
        }
        if (result instanceof CharSequence text) {
            return text.toString();
        }
        return String.valueOf(result);
    }
}
