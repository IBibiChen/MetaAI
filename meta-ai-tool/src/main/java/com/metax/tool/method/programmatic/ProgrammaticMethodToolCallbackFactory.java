package com.metax.tool.method.programmatic;

import com.metax.tool.catalog.MetaToolNames;
import com.metax.tool.specification.conversion.ToolResultConversionSupport;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.support.ToolDefinitions;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;

/**
 * ProgrammaticMethodToolCallbackFactory .
 *
 * <p>
 * Spring AI 编程式方法工具工厂，演示如何手动把 Java Method 包装成 MethodToolCallback
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
public class ProgrammaticMethodToolCallbackFactory {

    /**
     * 创建编程式当前日期时间工具回调
     *
     * <p>
     * 该方法完整展示官方底层路径：Method -> ToolDefinition -> ToolMetadata -> MethodToolCallback
     * 适合需要在代码中动态决定工具名称、描述、schema 或 returnDirect 策略的场景
     *
     * @param toolObject 普通 Java 工具对象
     * @return 编程式工具回调
     */
    public ToolCallback currentDateTimeCallback(ProgrammaticDateTimeTools toolObject) {
        Method method = currentDateTimeMethod();
        ToolDefinition toolDefinition = ToolDefinitions.builder(method)
                .name(MetaToolNames.METHOD_CALLBACK)
                .description("通过编程式 MethodToolCallback 获取指定 IANA 时区的当前日期时间")
                .build();
        ToolMetadata toolMetadata = ToolMetadata.builder()
                .returnDirect(false)
                .build();
        return MethodToolCallback.builder()
                .toolDefinition(toolDefinition)
                .toolMetadata(toolMetadata)
                .toolMethod(method)
                .toolObject(toolObject)
                .build();
    }

    /**
     * 创建带自定义结果转换器的编程式日期时间工具回调
     *
     * <p>
     * 该方法演示 MethodToolCallback.builder().toolCallResultConverter(...) 扩展点
     * 对 String 返回值，官方默认转换器会返回 JSON 字符串字面量，自定义转换器可以保持纯文本返回
     *
     * @param toolObject 普通 Java 工具对象
     * @return 带自定义结果转换器的编程式工具回调
     */
    public ToolCallback convertedCurrentDateTimeCallback(ProgrammaticDateTimeTools toolObject) {
        Method method = currentDateTimeMethod();
        ToolDefinition toolDefinition = ToolDefinitions.builder(method)
                .name(MetaToolNames.METHOD_CALLBACK_CONVERTED)
                .description("通过编程式 MethodToolCallback 获取指定 IANA 时区的当前日期时间，并使用自定义结果转换器")
                .build();
        ToolMetadata toolMetadata = ToolMetadata.builder()
                .returnDirect(false)
                .build();
        return MethodToolCallback.builder()
                .toolDefinition(toolDefinition)
                .toolMetadata(toolMetadata)
                .toolMethod(method)
                .toolObject(toolObject)
                .toolCallResultConverter(ToolResultConversionSupport.plainTextResultConverter())
                .build();
    }

    /**
     * 定位需要包装成工具的普通 Java 方法
     *
     * @return 当前日期时间方法反射对象
     */
    private Method currentDateTimeMethod() {
        Method method = ReflectionUtils.findMethod(ProgrammaticDateTimeTools.class, "currentDateTime", String.class);
        if (method == null) {
            throw new IllegalStateException("ProgrammaticDateTimeTools.currentDateTime(String) method not found");
        }
        return method;
    }
}
