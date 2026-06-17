package com.metax.agent.tool.config;

import com.metax.agent.tool.registry.MetaToolRegistry;
import com.metax.tool.catalog.MetaToolNames;
import com.metax.tool.foundation.DateTimeTools;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * MetaToolConfig .
 *
 * <p>
 * Spring AI 工具调用配置，集中声明全局基础工具和请求级工具链路需要的官方组件
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
@Configuration
public class MetaToolConfig {

    /**
     * 当前日期时间工具
     *
     * <p>
     * 无副作用基础工具，适合挂载到 ChatClient.defaultTools，也可进入请求级工具 allowlist 做官方路径参考验证
     * 该 Bean 不绑定业务 Repository、ChatMemory、VectorStore 或外部 client
     *
     * @return 当前日期时间工具
     */
    @Bean
    public DateTimeTools dateTimeTools() {
        return new DateTimeTools();
    }

    /**
     * 请求级工具回调提供器
     *
     * <p>
     * 使用 Spring AI 官方 MethodToolCallbackProvider 从 @Tool 方法生成 ToolCallback
     * 该 Provider 只声明可被请求级工具链路选择的工具，具体暴露范围仍由 MetaToolRegistry allowlist 决定
     *
     * @param dateTimeTools 当前日期时间工具
     * @return 请求级工具回调提供器
     */
    @Bean
    public ToolCallbackProvider metaRuntimeToolCallbackProvider(DateTimeTools dateTimeTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(dateTimeTools)
                .build();
    }

    /**
     * 请求级工具调用 Advisor
     *
     * <p>
     * 使用 Spring AI 官方 ToolCallAdvisor 把工具调用循环纳入 ChatClient advisor 链
     * 该 Advisor 只在 ToolChatService 中按请求追加，不进入基础 ChatClient 默认链路
     * advisorOrder 沿用官方默认语义，让工具调用 Advisor 在请求处理阶段优先进入，在响应处理阶段最后退出
     * suppressToolCallStreaming 用于过滤流式场景中的中间工具调用响应，只向下游暴露最终回答
     *
     * @param toolCallingManager Spring AI 工具调用管理器
     * @return 请求级工具调用 Advisor
     */
    @Bean
    public ToolCallAdvisor metaToolCallAdvisor(ToolCallingManager toolCallingManager) {
        return ToolCallAdvisor.builder()
                .toolCallingManager(toolCallingManager)
                .advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE + 300)
                .suppressToolCallStreaming()
                .build();
    }

    /**
     * 请求级工具注册表
     *
     * <p>
     * 统一封装工具名称 allowlist 和 ToolCallback 解析逻辑，避免 Controller 直接暴露 Spring Bean 或方法名
     * 当前阶段只允许当前时间工具，后续业务工具必须先补权限、审计和测试后再加入 allowlist
     *
     * @param metaRuntimeToolCallbackProvider 请求级工具回调提供器
     * @return 请求级工具注册表
     */
    @Bean
    public MetaToolRegistry metaToolRegistry(ToolCallbackProvider metaRuntimeToolCallbackProvider) {
        return new MetaToolRegistry(metaRuntimeToolCallbackProvider, Set.of(MetaToolNames.CURRENT_DATE_TIME));
    }
}
