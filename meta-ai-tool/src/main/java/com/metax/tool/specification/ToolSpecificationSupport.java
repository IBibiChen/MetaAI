package com.metax.tool.specification;

import com.metax.tool.catalog.MetaToolNames;
import com.metax.tool.function.model.DateTimeToolRequest;
import com.metax.tool.function.programmatic.DateTimeFunctionTools;
import com.metax.tool.specification.conversion.ToolResultConversionSupport;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.List;

/**
 * ToolSpecificationSupport .
 *
 * <p>
 * 工具契约参考支持类，集中演示 ToolDefinition、ToolMetadata 和重复工具名校验
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
public class ToolSpecificationSupport {

    /**
     * 创建 returnDirect 工具回调
     *
     * <p>
     * returnDirect 为 true 时，ToolCallAdvisor 会把工具执行结果直接返回给调用方，不再交回模型继续组织回答
     * 该示例只服务参考接口，生产系统应谨慎用于短路类工具，例如下载链接、结构化查询结果或确定性命令结果
     *
     * @param tools 函数工具集合
     * @return 直接返回工具结果的回调
     */
    public ToolCallback returnDirectDateTimeCallback(DateTimeFunctionTools tools) {
        return FunctionToolCallback.builder(MetaToolNames.SPEC_RETURN_DIRECT,
                        tools.zonedDateTimeFunction())
                .description("获取指定 IANA 时区的当前日期时间，并将工具结果直接返回给调用方")
                .inputType(DateTimeToolRequest.class)
                .toolMetadata(ToolMetadata.builder().returnDirect(true).build())
                .toolCallResultConverter(ToolResultConversionSupport.plainTextResultConverter())
                .build();
    }

    /**
     * 校验工具名称唯一性
     *
     * <p>
     * Spring AI 会拒绝重复工具名，参考代码显式调用该方法用于提醒维护者在工具进入模型前先检查契约
     *
     * @param callbacks 待暴露的工具回调列表
     */
    public void validateUniqueToolNames(List<ToolCallback> callbacks) {
        ToolCallingChatOptions.validateToolCallbacks(callbacks);
    }
}
