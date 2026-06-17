package com.metax.tool.function.programmatic;

import com.metax.tool.catalog.MetaToolNames;
import com.metax.tool.specification.conversion.ToolResultConversionSupport;
import com.metax.tool.function.model.AuditToolRequest;
import com.metax.tool.function.model.DateTimeToolRequest;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * FunctionToolCallbackFactory .
 *
 * <p>
 * Spring AI 编程式函数工具工厂，演示如何把 Java Function 系列接口包装成 FunctionToolCallback
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
public class FunctionToolCallbackFactory {

    /**
     * 创建指定时区日期时间函数工具
     *
     * <p>
     * 该方法展示 FunctionToolCallback 的核心路径：name、function、description、inputType、metadata 和结果转换器
     *
     * @param tools 函数工具集合
     * @return 函数工具回调
     */
    public ToolCallback zonedDateTimeCallback(DateTimeFunctionTools tools) {
        return FunctionToolCallback.builder(MetaToolNames.FUNCTION_CALLBACK,
                        tools.zonedDateTimeFunction())
                .description("通过 FunctionToolCallback 获取指定 IANA 时区的当前日期时间")
                .inputType(DateTimeToolRequest.class)
                .toolMetadata(ToolMetadata.builder().returnDirect(false).build())
                .toolCallResultConverter(ToolResultConversionSupport.plainTextResultConverter())
                .build();
    }

    /**
     * 创建无入参 Supplier 日期时间函数工具
     *
     * @param tools 函数工具集合
     * @return 函数工具回调
     */
    public ToolCallback currentUtcDateTimeSupplierCallback(DateTimeFunctionTools tools) {
        return FunctionToolCallback.builder(MetaToolNames.FUNCTION_SUPPLIER,
                        tools.currentUtcDateTimeSupplier())
                .description("通过 Supplier 函数工具获取当前 UTC 日期时间")
                .toolMetadata(ToolMetadata.builder().returnDirect(false).build())
                .build();
    }

    /**
     * 创建无返回值 Consumer 审计函数工具
     *
     * @param tools 函数工具集合
     * @return 函数工具回调
     */
    public ToolCallback auditConsumerCallback(DateTimeFunctionTools tools) {
        return FunctionToolCallback.builder(MetaToolNames.FUNCTION_CONSUMER,
                        tools.auditConsumer())
                .description("通过 Consumer 函数工具记录轻量级审计事件")
                .inputType(AuditToolRequest.class)
                .toolMetadata(ToolMetadata.builder().returnDirect(false).build())
                .build();
    }

    /**
     * 创建可读取 ToolContext 的 BiFunction 日期时间函数工具
     *
     * @param tools 函数工具集合
     * @return 函数工具回调
     */
    public ToolCallback contextualDateTimeCallback(DateTimeFunctionTools tools) {
        return FunctionToolCallback.builder(MetaToolNames.FUNCTION_CONTEXT,
                        tools.contextualDateTimeFunction())
                .description("通过 BiFunction 函数工具获取当前日期时间，并读取请求级 ToolContext")
                .inputType(DateTimeToolRequest.class)
                .toolMetadata(ToolMetadata.builder().returnDirect(false).build())
                .toolCallResultConverter(ToolResultConversionSupport.plainTextResultConverter())
                .build();
    }
}
