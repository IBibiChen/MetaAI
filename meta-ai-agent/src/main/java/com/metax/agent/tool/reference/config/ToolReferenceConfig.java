package com.metax.agent.tool.reference.config;

import com.metax.tool.catalog.MetaToolNames;
import com.metax.tool.function.model.DateTimeToolRequest;
import com.metax.tool.function.programmatic.DateTimeFunctionTools;
import com.metax.tool.function.programmatic.FunctionToolCallbackFactory;
import com.metax.tool.method.declarative.DeclarativeDateTimeTools;
import com.metax.tool.method.programmatic.ProgrammaticDateTimeTools;
import com.metax.tool.method.programmatic.ProgrammaticMethodToolCallbackFactory;
import com.metax.tool.execution.ToolCallingOptionsFactory;
import com.metax.tool.specification.ToolSpecificationSupport;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.SpringBeanToolCallbackResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.context.support.GenericApplicationContext;

import java.util.function.Function;

/**
 * ToolReferenceConfig .
 *
 * <p>
 * Spring AI 官方工具调用参考配置，集中装配 Methods as Tools 和 Functions as Tools 示例 Bean
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
@Configuration
public class ToolReferenceConfig {

    /**
     * 声明式方法工具对象
     *
     * <p>
     * 适用于 @Tool 官方路径，调用侧可以通过 ChatClient.tools 直接暴露，也可以通过 ToolCallbacks.from 转成 ToolCallback
     * 该 Bean 只服务参考接口，不进入生产工具 allowlist
     *
     * @return 声明式日期时间工具对象
     */
    @Bean
    public DeclarativeDateTimeTools referenceMethodTools() {
        return new DeclarativeDateTimeTools();
    }

    /**
     * 编程式方法工具对象
     *
     * <p>
     * 适用于 MethodToolCallback 官方路径，方法本身不带 @Tool，是否暴露给模型由外部 ToolCallback 决定
     * 该 Bean 只服务参考接口，不进入生产工具 allowlist
     *
     * @return 编程式日期时间工具对象
     */
    @Bean
    public ProgrammaticDateTimeTools referenceMethodTarget() {
        return new ProgrammaticDateTimeTools();
    }

    /**
     * 编程式方法工具工厂
     *
     * <p>
     * 负责演示 Method -> ToolDefinition -> ToolMetadata -> MethodToolCallback 的完整底层组装过程
     * 该 Bean 不负责业务工具注册
     *
     * @return 编程式方法工具工厂
     */
    @Bean
    public ProgrammaticMethodToolCallbackFactory methodCallbackFactory() {
        return new ProgrammaticMethodToolCallbackFactory();
    }

    /**
     * 编程式方法 ToolCallback
     *
     * <p>
     * 使用 Spring AI MethodToolCallback 直接包装普通 Java 方法
     * 该 Bean 可被请求级 toolCallbacks 使用，也可被 defaultToolCallbacks 挂到参考 ChatClient
     *
     * @param methodCallbackFactory 编程式方法工具工厂
     * @param referenceMethodTarget 编程式方法工具对象
     * @return 编程式方法工具回调
     */
    @Bean
    public ToolCallback methodCallback(ProgrammaticMethodToolCallbackFactory methodCallbackFactory,
                                       ProgrammaticDateTimeTools referenceMethodTarget) {
        return methodCallbackFactory.currentDateTimeCallback(referenceMethodTarget);
    }

    /**
     * 函数工具集合
     *
     * <p>
     * 适用于 FunctionToolCallback 官方路径，集中提供 Function、Supplier、Consumer 和 BiFunction 示例
     * 该 Bean 不直接暴露给模型，具体暴露由 FunctionToolCallbackFactory 控制
     *
     * @return 函数工具集合
     */
    @Bean
    public DateTimeFunctionTools referenceFunctionTools() {
        return new DateTimeFunctionTools();
    }

    /**
     * 编程式函数工具工厂
     *
     * <p>
     * 负责演示 FunctionToolCallback.builder 的完整组装过程
     * 该 Bean 不负责业务工具注册
     *
     * @return 编程式函数工具工厂
     */
    @Bean
    public FunctionToolCallbackFactory functionCallbackFactory() {
        return new FunctionToolCallbackFactory();
    }

    /**
     * 工具契约参考支持类
     *
     * <p>
     * 负责演示 ToolDefinition、ToolMetadata、returnDirect 和重复工具名校验
     * 该 Bean 只服务参考接口，不负责生产工具注册
     *
     * @return 工具契约参考支持类
     */
    @Bean
    public ToolSpecificationSupport specificationSupport() {
        return new ToolSpecificationSupport();
    }

    /**
     * 工具执行选项工厂
     *
     * <p>
     * 负责演示 ToolCallingChatOptions 在 ChatModel 手动执行、ToolCallAdvisor 执行和按名称解析工具时的差异
     * 该 Bean 只服务参考接口和测试，不绑定具体模型
     *
     * @return 工具执行选项工厂
     */
    @Bean
    public ToolCallingOptionsFactory executionOptionsFactory() {
        return new ToolCallingOptionsFactory();
    }

    /**
     * 编程式 Function ToolCallback
     *
     * <p>
     * 绑定 DateTimeToolRequest 入参类型，适合演示有结构化入参和返回值的函数工具
     *
     * @param functionCallbackFactory 编程式函数工具工厂
     * @param referenceFunctionTools  函数工具集合
     * @return 编程式函数工具回调
     */
    @Bean
    public ToolCallback functionCallback(FunctionToolCallbackFactory functionCallbackFactory,
                                         DateTimeFunctionTools referenceFunctionTools) {
        return functionCallbackFactory.zonedDateTimeCallback(referenceFunctionTools);
    }

    /**
     * 编程式 Supplier ToolCallback
     *
     * <p>
     * 绑定无入参 Supplier，适合演示官方支持的空入参函数工具
     *
     * @param functionCallbackFactory 编程式函数工具工厂
     * @param referenceFunctionTools  函数工具集合
     * @return 编程式 Supplier 工具回调
     */
    @Bean
    public ToolCallback supplierCallback(FunctionToolCallbackFactory functionCallbackFactory,
                                         DateTimeFunctionTools referenceFunctionTools) {
        return functionCallbackFactory.currentUtcDateTimeSupplierCallback(referenceFunctionTools);
    }

    /**
     * 编程式 Consumer ToolCallback
     *
     * <p>
     * 绑定无返回值 Consumer，只用于说明官方支持形态，不建议初期接入真实写操作
     *
     * @param functionCallbackFactory 编程式函数工具工厂
     * @param referenceFunctionTools  函数工具集合
     * @return 编程式 Consumer 工具回调
     */
    @Bean
    public ToolCallback consumerCallback(FunctionToolCallbackFactory functionCallbackFactory,
                                         DateTimeFunctionTools referenceFunctionTools) {
        return functionCallbackFactory.auditConsumerCallback(referenceFunctionTools);
    }

    /**
     * 编程式 BiFunction ToolCallback
     *
     * <p>
     * 绑定 ToolContext 入参，适合演示工具执行期读取租户、用户和会话上下文
     *
     * @param functionCallbackFactory 编程式函数工具工厂
     * @param referenceFunctionTools  函数工具集合
     * @return 编程式 BiFunction 工具回调
     */
    @Bean
    public ToolCallback contextCallback(FunctionToolCallbackFactory functionCallbackFactory,
                                        DateTimeFunctionTools referenceFunctionTools) {
        return functionCallbackFactory.contextualDateTimeCallback(referenceFunctionTools);
    }

    /**
     * Tool Specification returnDirect ToolCallback
     *
     * <p>
     * 绑定 ToolMetadata.returnDirect(true)，专门演示工具执行结果直接返回调用方的契约语义
     * 该 Bean 只进入参考接口，不进入生产默认工具或请求级 allowlist
     *
     * @param specificationSupport   工具契约参考支持类
     * @param referenceFunctionTools 函数工具集合
     * @return 直接返回工具结果的工具回调
     */
    @Bean
    public ToolCallback returnDirectCallback(ToolSpecificationSupport specificationSupport,
                                             DateTimeFunctionTools referenceFunctionTools) {
        return specificationSupport.returnDirectDateTimeCallback(referenceFunctionTools);
    }

    /**
     * Spring Bean Function 工具
     *
     * <p>
     * 该 Bean 使用官方 SpringBeanToolCallbackResolver 按名称解析为 FunctionToolCallback
     * Bean 名就是模型侧工具名，@Description 会成为工具描述
     *
     * @param tools 函数工具集合
     * @return Spring Bean Function 工具
     */
    @Bean(MetaToolNames.FUNCTION_BEAN)
    @Description("通过 Spring Bean Function 获取指定 IANA 时区的当前日期时间")
    public Function<DateTimeToolRequest, String> functionBean(DateTimeFunctionTools tools) {
        return tools.zonedDateTimeFunction();
    }

    /**
     * Spring Bean 工具回调解析器
     *
     * <p>
     * 使用 Spring AI 官方 SpringBeanToolCallbackResolver 按 Bean 名把 Function、Supplier、Consumer 或 BiFunction 解析为 ToolCallback
     * 该 Bean 只服务参考接口，不改变生产 ToolCallingManager 的默认解析策略
     *
     * @param applicationContext Spring 应用上下文
     * @return Spring Bean 工具回调解析器
     */
    @Bean
    public SpringBeanToolCallbackResolver springBeanCallbackResolver(
            GenericApplicationContext applicationContext) {
        return SpringBeanToolCallbackResolver.builder()
                .applicationContext(applicationContext)
                .build();
    }

    /**
     * 工具参考 ChatClient
     *
     * <p>
     * 不挂载默认工具，所有工具都必须在本轮请求中通过 tools 或 toolCallbacks 显式暴露
     * 适用于对比 runtime tools、runtime callbacks 和 Spring Bean resolver 路径
     *
     * @param chatModel 当前配置选中的 ChatModel
     * @return 工具参考 ChatClient
     */
    @Bean
    public ChatClient toolReferenceChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("你是 Spring AI 工具调用参考示例助手，回答必须简洁，并优先使用当前请求显式暴露的工具")
                .build();
    }

    /**
     * 方法工具默认回调参考 ChatClient
     *
     * <p>
     * 通过 defaultToolCallbacks 全局挂载编程式 MethodToolCallback，专门演示默认工具回调路径
     * 该 Bean 是参考专用客户端，避免默认工具污染生产 chatClient、ragChatClient 或 toolChatClient
     *
     * @param chatModel      当前配置选中的 ChatModel
     * @param methodCallback 编程式方法工具回调
     * @return 方法工具默认回调参考 ChatClient
     */
    @Bean
    public ChatClient defaultMethodToolChatClient(ChatModel chatModel,
                                                  ToolCallback methodCallback) {
        return ChatClient.builder(chatModel)
                .defaultSystem("你是 Spring AI 默认方法工具回调参考示例助手，回答必须简洁，并优先使用默认挂载的方法工具")
                .defaultToolCallbacks(methodCallback)
                .build();
    }

    /**
     * 函数工具默认回调参考 ChatClient
     *
     * <p>
     * 通过 defaultToolCallbacks 全局挂载编程式 FunctionToolCallback，专门演示函数工具默认回调路径
     * 该 Bean 是参考专用客户端，生产系统应只把无副作用基础工具挂成默认工具
     *
     * @param chatModel        当前配置选中的 ChatModel
     * @param functionCallback 编程式函数工具回调
     * @return 函数工具默认回调参考 ChatClient
     */
    @Bean
    public ChatClient defaultFunctionToolChatClient(ChatModel chatModel,
                                                    ToolCallback functionCallback) {
        return ChatClient.builder(chatModel)
                .defaultSystem("你是 Spring AI 默认函数工具回调参考示例助手，回答必须简洁，并优先使用默认挂载的函数工具")
                .defaultToolCallbacks(functionCallback)
                .build();
    }
}
