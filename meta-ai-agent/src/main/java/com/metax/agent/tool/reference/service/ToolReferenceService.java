package com.metax.agent.tool.reference.service;

import com.metax.tool.catalog.MetaToolNames;
import com.metax.tool.context.MetaToolContextKeys;
import com.metax.tool.execution.ToolCallingOptionsFactory;
import com.metax.tool.method.declarative.DeclarativeDateTimeTools;
import com.metax.tool.reference.ToolReferencePath;
import com.metax.tool.reference.ToolReferenceResponse;
import com.metax.tool.specification.ToolCallbackDefinitionView;
import com.metax.tool.specification.ToolSpecificationSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ToolReferenceService .
 *
 * <p>
 * Spring AI 官方工具调用参考服务，集中演示 Methods as Tools 和 Functions as Tools 的推荐实现路径
 * 该服务依赖 Lombok 生成构造器完成 Bean 注入
 * 多个同类型 ChatClient、ToolCallback 和 ToolCallbackResolver 通过字段名对齐 @Bean 方法名绑定到指定参考 Bean
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
@Service
@RequiredArgsConstructor
public class ToolReferenceService {

    private final ChatClient toolReferenceChatClient;

    private final ChatClient defaultMethodToolChatClient;

    private final ChatClient defaultFunctionToolChatClient;

    private final DeclarativeDateTimeTools referenceMethodTools;

    private final ToolCallback methodCallback;

    private final ToolCallback functionCallback;

    private final ToolCallback supplierCallback;

    private final ToolCallback consumerCallback;

    private final ToolCallback contextCallback;

    private final ToolCallback returnDirectCallback;

    private final ToolCallbackResolver springBeanCallbackResolver;

    private final ToolSpecificationSupport specificationSupport;

    private final ToolCallingOptionsFactory executionOptionsFactory;

    /**
     * 演示 @Tool 对象通过 ChatClient.tools 在单次请求暴露
     *
     * @param prompt 用户提示词
     * @return 工具参考响应
     */
    public ToolReferenceResponse methodDeclarativeRuntimeTools(String prompt) {
        String answer = toolReferenceChatClient.prompt()
                // tools 接收带 @Tool 方法的对象，Spring AI 会在本轮请求中把这些方法转换成工具
                .tools(referenceMethodTools)
                .user(messageOrDefault(prompt))
                .call()
                .content();
        return response(answer, ToolReferencePath.METHOD_DECLARATIVE_RUNTIME_TOOLS,
                declarativeCallbacks(),
                List.of("这是最直接的 Methods as Tools 声明式路径：把带 @Tool 方法的对象传给 ChatClient.tools",
                        "工具只在本轮请求可见，不会变成全局默认工具"));
    }

    /**
     * 演示 @Tool 对象通过 ToolCallbacks.from 转成 ToolCallback 后在单次请求暴露
     *
     * @param prompt 用户提示词
     * @return 工具参考响应
     */
    public ToolReferenceResponse methodDeclarativeRuntimeCallbacks(String prompt) {
        ToolCallback[] callbacks = declarativeCallbacks();
        String answer = toolReferenceChatClient.prompt()
                // ToolCallbacks.from 会扫描 @Tool 方法并生成 ToolCallback，适合先观察工具定义再传给 ChatClient 的场景
                .toolCallbacks(callbacks)
                .user(messageOrDefault(prompt))
                .call()
                .content();
        return response(answer, ToolReferencePath.METHOD_DECLARATIVE_RUNTIME_CALLBACKS, callbacks,
                List.of("这是声明式方法工具的回调路径：@Tool 对象先转成 ToolCallback，再交给 ChatClient",
                        "它比 tools 更显式，便于做 allowlist、去重和工具定义检查"));
    }

    /**
     * 演示 MethodToolCallback 在单次请求暴露
     *
     * @param prompt 用户提示词
     * @return 工具参考响应
     */
    public ToolReferenceResponse methodProgrammaticRuntimeCallbacks(String prompt) {
        String answer = toolReferenceChatClient.prompt()
                // 编程式路径不依赖 @Tool 注解，ToolCallback 已经显式持有工具定义、metadata、method 和 target object
                .toolCallbacks(methodCallback)
                .user(messageOrDefault(prompt))
                .call()
                .content();
        return response(answer, ToolReferencePath.METHOD_PROGRAMMATIC_RUNTIME_CALLBACKS,
                new ToolCallback[]{methodCallback},
                List.of("这是 Methods as Tools 底层编程式路径：普通 Java Method 被 MethodToolCallback 包装成工具",
                        "适合动态工具、框架生成工具或需要手动控制 ToolDefinition 的场景"));
    }

    /**
     * 演示 MethodToolCallback 通过 defaultToolCallbacks 在专用 ChatClient 全局暴露
     *
     * @param prompt 用户提示词
     * @return 工具参考响应
     */
    public ToolReferenceResponse methodProgrammaticDefaultCallbacks(String prompt) {
        String answer = defaultMethodToolChatClient.prompt()
                // 这个 ChatClient 已经通过 defaultToolCallbacks 挂载编程式方法工具，所以本轮请求不用再传 toolCallbacks
                .user(messageOrDefault(prompt))
                .call()
                .content();
        return response(answer, ToolReferencePath.METHOD_PROGRAMMATIC_DEFAULT_CALLBACKS,
                new ToolCallback[]{methodCallback},
                List.of("这是 defaultToolCallbacks 路径：工具对该参考 ChatClient 的所有请求默认可见",
                        "生产系统必须谨慎使用默认工具，只能放无副作用、无权限边界的基础工具"));
    }

    /**
     * 演示 FunctionToolCallback 在单次请求暴露
     *
     * @param prompt 用户提示词
     * @return 工具参考响应
     */
    public ToolReferenceResponse functionProgrammaticRuntimeCallbacks(String prompt) {
        ToolCallback[] callbacks = functionCallbacks();
        String answer = toolReferenceChatClient.prompt()
                // FunctionToolCallback 直接持有函数、入参类型、工具定义、metadata 和结果转换器
                .toolCallbacks(callbacks)
                .toolContext(referenceToolContext())
                .user(functionMessageOrDefault(prompt))
                .call()
                .content();
        return response(answer, ToolReferencePath.FUNCTION_PROGRAMMATIC_RUNTIME_CALLBACKS, callbacks,
                List.of("这是 Functions as Tools 编程式路径：Function、Supplier、Consumer 和 BiFunction 被包装成 FunctionToolCallback",
                        "BiFunction 的第二个参数是 ToolContext，适合读取不会发送给模型的请求上下文"));
    }

    /**
     * 演示 FunctionToolCallback 通过 defaultToolCallbacks 在专用 ChatClient 全局暴露
     *
     * @param prompt 用户提示词
     * @return 工具参考响应
     */
    public ToolReferenceResponse functionProgrammaticDefaultCallbacks(String prompt) {
        String answer = defaultFunctionToolChatClient.prompt()
                // 这个 ChatClient 已经通过 defaultToolCallbacks 挂载编程式函数工具，所以本轮请求不用再传 toolCallbacks
                .user(functionMessageOrDefault(prompt))
                .call()
                .content();
        return response(answer, ToolReferencePath.FUNCTION_PROGRAMMATIC_DEFAULT_CALLBACKS,
                new ToolCallback[]{functionCallback},
                List.of("这是函数工具 defaultToolCallbacks 路径：FunctionToolCallback 对该参考 ChatClient 默认可见",
                        "默认函数工具同样要控制副作用，不能把写数据库、发外部请求等业务动作随意默认暴露"));
    }

    /**
     * 演示 Spring Bean 函数通过 SpringBeanToolCallbackResolver 解析后暴露
     *
     * @param prompt 用户提示词
     * @return 工具参考响应
     */
    public ToolReferenceResponse functionBeanResolverCallbacks(String prompt) {
        ToolCallback callback = resolveSpringBeanFunctionCallback();
        String answer = toolReferenceChatClient.prompt()
                // SpringBeanToolCallbackResolver 按 Bean 名解析 Function Bean，解析结果仍然是统一的 ToolCallback
                .toolCallbacks(callback)
                .user(functionMessageOrDefault(prompt))
                .call()
                .content();
        return response(answer, ToolReferencePath.FUNCTION_BEAN_RESOLVER_CALLBACKS,
                new ToolCallback[]{callback},
                List.of("这是 Spring Bean 函数路径：Bean 名就是工具名，@Description 会成为工具描述",
                        "解析完成后仍回到统一 ToolCallback 抽象，后续可继续做 allowlist 和定义检查"));
    }

    /**
     * 返回所有参考工具定义
     *
     * @return 工具参考响应
     */
    public ToolReferenceResponse definitions() {
        ToolCallback[] callbacks = allCallbacks();
        return response("工具定义只用于观察 Spring AI 交给模型的工具契约，不调用模型",
                ToolReferencePath.DEFINITIONS_ONLY, callbacks,
                List.of("ToolDefinition 是模型实际看到的工具契约",
                        "重点观察 name、description、inputSchema 和 returnDirect",
                        "方法工具和函数工具最终都会收敛到 ToolCallback"));
    }

    /**
     * 返回工具契约参考信息
     *
     * @return 工具参考响应
     */
    public ToolReferenceResponse specification() {
        ToolCallback[] callbacks = new ToolCallback[]{returnDirectCallback};
        specificationSupport.validateUniqueToolNames(List.of(callbacks));
        return response("Tool Specification 描述模型实际看到的工具契约，不等同于 Java 方法签名",
                ToolReferencePath.SPECIFICATION_DEFINITIONS, callbacks,
                List.of("ToolDefinition 包含 name、description 和 inputSchema",
                        "ToolMetadata.returnDirect 为 true 时，工具执行结果会直接返回调用方",
                        "工具进入模型前必须检查重复名称，否则 Spring AI 会拒绝执行"));
    }

    /**
     * 返回工具执行选项参考信息
     *
     * @return 工具参考响应
     */
    public ToolReferenceResponse execution() {
        ToolCallback[] callbacks = new ToolCallback[]{functionCallback};
        ToolCallingChatOptions frameworkOptions = executionOptionsFactory.frameworkControlledOptions(
                List.of(callbacks), referenceToolContext());
        ToolCallingChatOptions userOptions = executionOptionsFactory.userControlledOptions(
                List.of(callbacks), referenceToolContext());
        ToolCallingChatOptions advisorOptions = executionOptionsFactory.advisorControlledOptions(
                List.of(callbacks), referenceToolContext());
        ToolCallingChatOptions namedOptions = executionOptionsFactory.namedToolOptions(
                Set.of(MetaToolNames.FUNCTION_CALLBACK), referenceToolContext());
        return response("Tool Execution 决定工具调用由 ChatModel 内部执行、调用方手动执行，还是由 ToolCallAdvisor 接管循环",
                ToolReferencePath.EXECUTION_OPTIONS, callbacks,
                List.of("framework internalToolExecutionEnabled = "
                                + frameworkOptions.getInternalToolExecutionEnabled(),
                        "user internalToolExecutionEnabled = " + userOptions.getInternalToolExecutionEnabled(),
                        "advisor internalToolExecutionEnabled = " + advisorOptions.getInternalToolExecutionEnabled(),
                        "named toolNames = " + namedOptions.getToolNames(),
                        "ToolCallAdvisor 会在 advisor 链中复制选项并关闭内部工具执行",
                        "ToolContext 只给工具执行侧读取，不会作为 schema 暴露给模型"));
    }

    /**
     * 获取声明式方法工具回调
     *
     * <p>
     * 该方法通过 Spring AI ToolCallbacks.from 扫描 @Tool 方法
     * 适用于需要在进入 ChatClient 前观察 ToolDefinition 的参考接口
     *
     * @return 声明式方法工具回调数组
     */
    private ToolCallback[] declarativeCallbacks() {
        return ToolCallbacks.from(referenceMethodTools);
    }

    /**
     * 获取编程式函数工具回调集合
     *
     * <p>
     * 当前集合覆盖 Function、Supplier、Consumer 和 BiFunction 四种官方函数工具形态
     * BiFunction 示例会在工具执行期读取 ToolContext
     *
     * @return 编程式函数工具回调数组
     */
    private ToolCallback[] functionCallbacks() {
        return new ToolCallback[]{
                functionCallback,
                supplierCallback,
                consumerCallback,
                contextCallback
        };
    }

    /**
     * 汇总所有参考工具回调
     *
     * <p>
     * 该方法只服务定义查看接口，用于一次性展示 Methods as Tools、Functions as Tools、Spring Bean resolver 和 returnDirect 示例
     * 它不负责生产工具注册，生产请求级工具仍由 MetaToolRegistry 控制 allowlist
     *
     * @return 当前参考接口可展示的全部工具回调
     */
    private ToolCallback[] allCallbacks() {
        ToolCallback[] declarativeCallbacks = declarativeCallbacks();
        ToolCallback[] functionCallbacks = functionCallbacks();
        ToolCallback springBeanFunctionCallback = resolveSpringBeanFunctionCallback();
        ToolCallback[] callbacks = Arrays.copyOf(declarativeCallbacks, declarativeCallbacks.length + 3
                + functionCallbacks.length);
        callbacks[declarativeCallbacks.length] = methodCallback;
        System.arraycopy(functionCallbacks, 0, callbacks, declarativeCallbacks.length + 1, functionCallbacks.length);
        callbacks[callbacks.length - 2] = springBeanFunctionCallback;
        callbacks[callbacks.length - 1] = returnDirectCallback;
        return callbacks;
    }

    /**
     * 解析 Spring Bean 函数工具回调
     *
     * <p>
     * SpringBeanToolCallbackResolver 按 Bean 名解析函数工具
     * 未命中说明参考配置和工具名称常量已经漂移，需要直接失败暴露配置问题
     *
     * @return Spring Bean 函数工具回调
     */
    private ToolCallback resolveSpringBeanFunctionCallback() {
        ToolCallback callback = springBeanCallbackResolver.resolve(
                MetaToolNames.FUNCTION_BEAN);
        if (callback == null) {
            throw new IllegalStateException("未找到 Spring Bean 函数工具回调："
                    + MetaToolNames.FUNCTION_BEAN);
        }
        return callback;
    }

    /**
     * 组装工具参考响应
     *
     * <p>
     * 响应同时返回模型回答、参考路径、工具名称和 ToolDefinition 视图
     * 初学者可以通过该结果直接观察模型实际看到的工具契约
     *
     * @param answer    模型回答或参考说明
     * @param path      当前参考接口路径标识
     * @param callbacks 本次展示的工具回调
     * @param notes     实现要点说明
     * @return 工具参考响应
     */
    private ToolReferenceResponse response(String answer,
                                           ToolReferencePath path,
                                           ToolCallback[] callbacks,
                                           List<String> notes) {
        List<ToolCallbackDefinitionView> definitions = Arrays.stream(callbacks)
                .map(ToolCallbackDefinitionView::from)
                .toList();
        List<String> toolNames = definitions.stream()
                .map(ToolCallbackDefinitionView::name)
                .toList();
        return new ToolReferenceResponse(answer, path, toolNames, definitions, notes);
    }

    /**
     * 创建参考工具执行上下文
     *
     * <p>
     * ToolContext 只在工具执行期可见，不进入模型可见 schema
     * 这里使用固定值是为了让参考接口稳定展示上下文函数工具的执行方式
     *
     * @return 参考工具上下文
     */
    private Map<String, Object> referenceToolContext() {
        return Map.of(
                MetaToolContextKeys.TENANT_ID, "referenceTenant",
                MetaToolContextKeys.USER_ID, "referenceUser",
                MetaToolContextKeys.CHAT_ID, "referenceChat"
        );
    }

    /**
     * 解析方法工具默认提示词
     *
     * @param prompt 原始提示词
     * @return 实际发送给模型的提示词
     */
    private String messageOrDefault(String prompt) {
        return prompt == null || prompt.isBlank()
                ? "请使用可用工具回答 Asia/Shanghai 当前日期时间"
                : prompt;
    }

    /**
     * 解析函数工具默认提示词
     *
     * @param prompt 原始提示词
     * @return 实际发送给模型的提示词
     */
    private String functionMessageOrDefault(String prompt) {
        return prompt == null || prompt.isBlank()
                ? "请使用函数工具回答 Asia/Shanghai 当前日期时间"
                : prompt;
    }
}
