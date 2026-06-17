# MetaAI Spring AI Tools 学习与最佳实践

本文件是 `com.metax.tool` 包的主文档，放在源码包目录下，方便阅读代码时同步查看官方路径说明

对应官方文档：<https://docs.spring.io/spring-ai/reference/1.1/api/tools.html>

当前项目按 Spring AI 1.1.8 路线实现工具调用示例。不要直接套用 Spring AI 2.x 示例，2.x 的 API 和行为需要单独评估

## 模块级总览

`meta-ai-tool` 是 MetaAI 的通用工具模块，负责沉淀跨业务可复用的工具定义、工具契约、上下文读取、执行选项、结果转换、异常处理和官方路径参考代码

`meta-ai-agent` 负责把这些通用工具挂到 `ChatClient.defaultTools`、请求级 `toolCallbacks` 或参考接口

当前时间工具 `DateTimeTools` 属于无副作用默认安全基础工具，可以作为全局默认工具

默认安全基础工具统一放在 `com.metax.tool.foundation`
该包按风险边界命名，不按具体工具领域命名
它不是 common 工具箱
只有不访问业务数据、不写状态、不依赖租户或用户上下文的工具才应放入该包
后续系统只读状态、简单格式化、无业务依赖转换工具可以放入该包

会读写业务数据、调用外部系统、依赖租户权限或产生副作用的工具不能直接放入基础默认链路。业务工具进入模型前必须经过服务端
allowlist、权限校验、审计策略、异常策略和测试覆盖

### 官方章节到本项目包的映射

这份文档按 Spring AI 1.1.8 官方 Tools 页面组织，但代码分包不机械复制官方目录

原因很简单：官方章节是学习顺序，工程分包是职责边界。把每个小标题都拆成包，会制造大量空包和概念噪声，初学者反而更难判断哪些类应该一起演进

官方章节对应关系如下：

- `Methods as Tools`：对应 `com.metax.tool.method`
- `Functions as Tools`：对应 `com.metax.tool.function`
- `Tool Specification`：对应 `com.metax.tool.specification`
- `Result Conversion`：对应 `com.metax.tool.specification.conversion`
- `Tool Context`：对应 `com.metax.tool.context`
- `Tool Execution`：对应 `com.metax.tool.execution`
- `Tool Resolution`：对应 `com.metax.tool.resolver`
- `Tool Argument Augmentation`：对应 `com.metax.tool.argument`
- `Observability`：由 agent 侧 `SimpleLoggerAdvisor`、`ToolCallAdvisor` 和参考接口结果共同承接，当前不单独建包

项目治理补充包如下：

- `com.metax.tool.catalog`：维护项目内稳定工具名和分类，不是官方 `ToolDefinition`
- `com.metax.tool.foundation`：维护可默认暴露的无副作用基础工具，不是 common 工具箱
- `com.metax.tool.reference`：维护参考接口响应模型和路径枚举，不是 Spring AI 官方抽象

### 最短学习路径

初学者不要一上来就看 `ToolCallAdvisor` 或 `ToolCallingManager`

更稳的阅读顺序是：

1. 先看 `foundation.DateTimeTools`，理解一个最小 @Tool 方法如何成为默认安全工具
2. 再看 `method.declarative`，理解 `@Tool`、`@ToolParam` 和声明式结果转换
3. 再看 `method.programmatic`，理解 `MethodToolCallback` 如何显式构造工具契约
4. 再看 `function.programmatic` 和 `function.model`，理解函数工具、结构化入参和 `ToolContext`
5. 再看 `specification`，理解模型真正看到的是 `ToolDefinition`
6. 再看 `execution`、`resolver`、`argument`，理解执行职责、名称解析和参数增强
7. 最后看 agent 模块的 `MetaToolConfig`、`ToolChatService`、`ToolReferenceService`，理解工具如何进入真实 ChatClient 链路

## 一、工具调用解决什么问题

大模型本身不会直接访问数据库、Redis、对象存储、第三方接口或当前系统时间

Spring AI Tools 解决的是“模型如何向应用提出调用外部能力的请求”，而不是“模型自己执行外部能力”

一次工具调用中，模型负责两件事：

- 判断当前问题是否需要调用工具
- 根据工具 schema 生成工具名和 JSON 参数

应用负责更多也更关键的事情：

- 决定本轮请求允许暴露哪些工具
- 把工具方法或函数包装成 `ToolCallback`
- 把 `ToolDefinition` 发给模型
- 根据模型返回的工具名找到真实工具
- 执行工具并处理权限、租户、审计、异常和结果转换
- 决定工具结果直接返回，还是交回模型继续组织最终回答

严厉一点说：把“模型能调用工具”理解成“模型能执行工具”，这是最危险的误解。执行权必须始终留在应用侧

### 信息检索类工具

信息检索类工具用于补足模型不知道的外部信息

典型场景：

- 查询数据库
- 查询业务服务
- 查询当前时间
- 查询文件或对象存储元数据
- 查询实时状态

这类工具主要关注权限过滤、数据脱敏、结果大小和异常降级

### 动作执行类工具

动作执行类工具会改变外部系统状态

典型场景：

- 创建记录
- 发送通知
- 调用审批流
- 删除文件
- 触发索引

这类工具风险更高，必须关注幂等、审计、二次确认、回滚、限流和异常抛出策略

`meta-ai-tool` 当前只沉淀无副作用基础工具和教学参考路径。动作执行类业务工具应放在具体业务模块

## 二、工具调用完整流程

工具调用不是一个注解，也不是一个 `Function`。它是一条完整的运行链路

### 1、应用选择本轮可见工具

应用先决定本轮请求允许模型看到哪些工具

入口可能是：

- `ChatClient.tools(...)`
- `ChatClient.toolCallbacks(...)`
- `ChatClient.toolNames(...)`
- `ChatClient.defaultTools(...)`
- `ChatClient.defaultToolCallbacks(...)`

本项目中，`MetaToolRegistry` 负责请求级 allowlist，客户端传入的 `toolNames` 只能在服务端 allowlist 中收窄，不能扩大工具权限

### 2、Spring AI 生成工具定义

Spring AI 会把方法工具或函数工具转换成统一的 `ToolCallback`

每个 `ToolCallback` 都能提供一个 `ToolDefinition`

`ToolDefinition` 包含模型真正能看到的工具契约：

- `name`
- `description`
- `inputSchema`

模型不会理解 Java 类结构，也不会看到工具内部实现。它只看到 `ToolDefinition`

### 3、模型判断是否调用工具

应用把用户问题和工具定义一起发给模型

模型根据 `description` 和 `inputSchema` 判断：

- 是否需要工具
- 调哪个工具
- 传什么参数

如果 `description` 写烂了，模型调用错工具不是模型的问题，是工具契约设计失败

### 4、模型返回工具调用请求

模型不会直接执行工具

它只返回类似这样的工具调用请求：

```
toolName=currentDateTime
toolArguments={"zoneId":"Asia/Shanghai"}
```

工具名和参数仍然需要应用侧解析、校验和执行

### 5、应用解析工具名并找到 ToolCallback

应用侧根据工具名找到对应 `ToolCallback`

常见路径：

- 请求中直接携带 `toolCallbacks`
- 使用 `ToolCallbackResolver` 根据 `toolNames` 解析
- 使用 `SpringBeanToolCallbackResolver` 按 Spring Bean 名解析函数工具
- 使用 `StaticToolCallbackResolver` 查静态工具表
- 使用 `DelegatingToolCallbackResolver` 串联多个解析器

### 6、应用执行工具

`ToolCallback.call(...)` 执行真实工具逻辑

如果工具需要安全上下文，调用侧通过 `ToolContext` 注入

`ToolContext` 不进入模型可见 schema，因此模型不能伪造 `tenantId`、`userId`、`chatId` 这些字段

### 7、应用处理工具结果或异常

工具返回值先经过 `ToolCallResultConverter` 转成模型可读字符串

工具异常交给 `ToolExecutionExceptionProcessor` 处理

异常可以反馈给模型继续回答，也可以直接抛给业务层

### 8、应用决定结果流向

如果 `returnDirect=false`，工具结果会作为下一轮消息交回模型，模型继续生成最终回答

如果 `returnDirect=true`，工具结果会直接返回调用方，不再交给模型二次加工

这一步决定了工具调用是“辅助模型回答”，还是“短路模型回答”

## 三、官方核心概念地图

Spring AI Tools 的核心不是某个单独类，而是一组互相配合的抽象

### 工具定义：Tool Definition（ToolDefinition）

作用：描述模型可见的工具契约

包含：`name`、`description`、`inputSchema`

本项目入口：`ToolSpecificationSupport`

理解重点：模型看到的是工具定义，不是 Java 方法、函数对象或业务代码

### 工具回调：Tool Callback（ToolCallback）

作用：Spring AI 统一工具抽象

包含两部分：

- `getToolDefinition()` 返回模型可见契约
- `call(...)` 执行真实工具逻辑

本项目入口：`ToolCallbackDefinitionView`、`ProgrammaticMethodToolCallbackFactory`、`FunctionToolCallbackFactory`

理解重点：无论工具来自 `@Tool`、`MethodToolCallback`、`FunctionToolCallback` 还是 resolver，最终都应该收敛到 `ToolCallback`

### 工具元数据：Tool Metadata（ToolMetadata）

作用：描述工具执行元信息

当前重点：`returnDirect`

本项目入口：`ToolSpecificationSupport`

理解重点：`returnDirect` 会改变工具结果流向，不只是返回格式开关

### 工具执行：Tool Execution

作用：模型提出工具调用后，应用真正执行工具并处理结果

涉及：工具查找、参数解析、方法或函数调用、结果转换、异常处理、结果回传

本项目入口：`ToolCallingOptionsFactory`、`ToolExceptionHandlingSupport`、`MetaToolConfig`

理解重点：Tool Execution 是应用侧职责，模型只提出调用请求

### 工具调用管理器：Tool Calling Manager（ToolCallingManager）

作用：协调工具调用请求、工具解析、工具执行和结果返回

本项目入口：`MetaToolConfig` 中的 `ToolCallAdvisor` 装配

理解重点：普通业务不应急着自定义 `ToolCallingManager`，除非确实要统一接管工具执行生命周期

### 工具调用 Advisor：Tool Call Advisor（ToolCallAdvisor）

作用：把工具调用循环纳入 `ChatClient` advisor 链

本项目入口：`MetaToolConfig`

理解重点：`ToolCallAdvisor` 会接管工具调用循环，并在 advisor 链中关闭 ChatModel 内部工具执行

### 工具调用选项：Tool Calling Chat Options（ToolCallingChatOptions）

作用：决定本轮注册哪些工具、传递哪些 `ToolContext`，以及是否启用内部工具执行

本项目入口：`ToolCallingOptionsFactory`

关键字段：

- `toolCallbacks`
- `toolNames`
- `toolContext`
- `internalToolExecutionEnabled`

理解重点：`internalToolExecutionEnabled=true` 和 `internalToolExecutionEnabled=false` 会让执行职责落在不同位置

### 工具上下文：Tool Context（ToolContext）

作用：调用侧注入给工具执行期读取的服务端上下文

本项目入口：`ToolContextAccessor`、`MetaToolContextKeys`

理解重点：`ToolContext` 不进入模型 schema，适合承载租户、用户、会话和审计边界

### 工具结果转换器：Tool Call Result Converter（ToolCallResultConverter）

作用：把工具返回值转换为模型可读取的字符串

本项目入口：`CustomToolCallResultConverter`、`ToolResultConversionSupport`

理解重点：方法工具和函数工具都可以使用同一类结果转换器。未显式设置 converter 时，默认转换行为可能和纯文本预期不一致

### 工具异常处理器：Tool Execution Exception Processor（ToolExecutionExceptionProcessor）

作用：决定工具异常是反馈给模型还是抛给调用方

本项目入口：`ToolExceptionHandlingSupport`

理解重点：无副作用工具可以把异常反馈给模型，写操作和敏感操作应优先抛给业务层

### 工具回调解析器：Tool Callback Resolver（ToolCallbackResolver）

作用：根据工具名解析 `ToolCallback`

本项目入口：`ToolResolverSupport`

理解重点：`toolNames` 通常配合 resolver 使用，适合按名称暴露工具

### 工具参数增强：Tool Argument Augmentation

作用：在已有工具 schema 外层追加增强参数，并在执行前触发参数事件

本项目入口：`ToolArgumentSupport`

理解重点：增强参数会进入模型可见 schema，不能替代 `ToolContext` 承载安全上下文

## 四、Methods as Tools

方法工具适合把已有 Java 对象上的方法暴露给模型

### 声明式方法工具：@Tool

`DeclarativeDateTimeTools` 演示官方声明式方法工具路径

`@Tool` 定义工具名称和描述

`@ToolParam` 定义参数描述和 `required` 语义

`@ToolParam(required = false)` 表达可选参数，模型可以省略该字段，由工具内部负责默认值回退

`@Tool(resultConverter = ...)` 是声明式方法工具绑定自定义结果转换器的官方入口

方法本身仍然是普通 Java 方法，只是在 Spring AI 扫描时被包装成 `ToolCallback`

### 请求级直接暴露：ChatClient.tools

`ChatClient.tools` 适合在单次请求中临时暴露带 `@Tool` 方法的对象

这种方式最直接，但调用侧不容易先观察 `ToolDefinition`

参考接口：`/v1/tools/reference/method/declarative/runtime`

```
chatClient.prompt()
        .tools(declarativeDateTimeTools)
        .user("请回答 Asia/Shanghai 当前时间")
        .call()
        .content();
```

### 显式回调暴露：ToolCallbacks.from

`ToolCallbacks.from` 会扫描 `@Tool` 对象并生成 `ToolCallback` 数组

该路径适合在进入 `ChatClient` 前做工具定义观察、去重、allowlist 和审计

参考接口：`/v1/tools/reference/method/declarative/callbacks`

```
ToolCallback[] callbacks = ToolCallbacks.from(declarativeDateTimeTools);

chatClient.prompt()
        .toolCallbacks(callbacks)
        .user("请回答 Asia/Shanghai 当前时间")
        .call()
        .content();
```

### 请求级注册表：MethodToolCallbackProvider

`MethodToolCallbackProvider` 用于把一组 `@Tool` 对象转成 `ToolCallbackProvider`

`MetaToolConfig` 使用它把 `DateTimeTools` 纳入请求级工具候选集

`MetaToolRegistry` 再基于服务端 allowlist 决定本轮实际暴露哪些工具

`DateTimeTools` 位于 `com.metax.tool.foundation`
它是默认安全基础工具，不是 Methods as Tools 教学示例专用类
`foundation` 不是按时间领域命名，而是按默认安全准入边界命名
声明式和编程式方法工具示例仍放在 `com.metax.tool.method`

### 编程式方法工具：MethodToolCallback

`ProgrammaticDateTimeTools` 本身不使用 `@Tool`

`ProgrammaticMethodToolCallbackFactory` 使用反射拿到 `Method`，再构造 `ToolDefinition`、`ToolMetadata` 和
`MethodToolCallback`

该路径适合工具名称、描述、schema 或 `returnDirect` 需要动态控制的场景

`MethodToolCallback.builder().toolCallResultConverter(...)` 是编程式方法工具绑定自定义结果转换器的官方入口，也是
`@Tool(resultConverter = ...)` 在编程式路径上的等价扩展点

参考接口：`/v1/tools/reference/method/programmatic/runtime` 和 `/v1/tools/reference/method/programmatic/default`

```
Method method = ReflectionUtils.findMethod(ProgrammaticDateTimeTools.class, "currentDateTime", String.class);

ToolDefinition definition = ToolDefinitions.builder(method)
        .name(MetaToolNames.METHOD_CALLBACK)
        .description("获取指定 IANA 时区的当前日期时间")
        .build();

ToolCallback callback = MethodToolCallback.builder()
        .toolDefinition(definition)
        .toolMetadata(ToolMetadata.builder().returnDirect(false).build())
        .toolMethod(method)
        .toolObject(programmaticDateTimeTools)
        .toolCallResultConverter(ToolResultConversionSupport.plainTextResultConverter())
        .build();
```

### Method Tool Limitations

方法工具适合包装已有 Java 对象的方法，但它不是万能入口

方法工具的主要限制如下：

- 方法签名会影响 schema 生成，复杂对象、嵌套对象和可选字段必须通过测试观察 `inputSchema`
- `@ToolParam` 描述写得不清楚时，模型会生成错误参数，不能指望模型自动理解 Java 语义
- 方法工具默认更容易贴近业务对象，暴露前必须确认没有把内部服务方法直接交给模型选择
- 带副作用的方法不能进入 `defaultTools`，必须走请求级 allowlist、权限、审计和异常策略
- 安全字段不能作为普通方法参数让模型生成，应走 `ToolContext`

一个常见错误是把 Controller 或 Service 上的现成方法直接加 `@Tool`

这会把业务边界、权限边界和模型可见 schema 混在一起。正确做法是为模型单独设计工具方法，让工具方法调用业务服务，而不是把业务服务方法本身当工具契约

## 五、Functions as Tools

函数工具适合把 Java `Function`、`Supplier`、`Consumer` 或 `BiFunction` 暴露给模型

`FunctionToolCallbackFactory` 负责把这些函数包装成 `ToolCallback`

函数工具没有 `@Tool(resultConverter = ...)` 注解路径。自定义结果转换器应通过
`FunctionToolCallback.builder().toolCallResultConverter(...)` 绑定

### 结构化入参函数工具：Function

`Function<DateTimeToolRequest, String>` 适合有明确输入和输出的纯函数工具

`DateTimeToolRequest` 使用 record 承载入参

`JsonClassDescription` 和 `JsonPropertyDescription` 帮助生成更清晰的 `inputSchema`

```
ToolCallback callback = FunctionToolCallback
        .builder(MetaToolNames.FUNCTION_CALLBACK, tools.zonedDateTimeFunction())
        .description("获取指定 IANA 时区的当前日期时间")
        .inputType(DateTimeToolRequest.class)
        .toolMetadata(ToolMetadata.builder().returnDirect(false).build())
        .toolCallResultConverter(ToolResultConversionSupport.plainTextResultConverter())
        .build();
```

### 无入参函数工具：Supplier

`Supplier<String>` 适合没有输入参数的工具

Spring AI 会使用 `Void` 作为输入类型并生成空对象 schema

这类工具常见于当前时间、系统状态、只读环境信息等场景

### 无返回值动作工具：Consumer

`Consumer<AuditToolRequest>` 适合动作型工具

参考代码只演示官方支持形态，不写日志、不写数据库

生产环境暴露 `Consumer` 必须特别谨慎，因为它天然容易承载副作用

### 上下文函数工具：BiFunction + ToolContext

`BiFunction<DateTimeToolRequest, ToolContext, String>` 适合同时接收模型参数和执行期上下文

模型只生成 `DateTimeToolRequest`

`tenantId`、`userId` 和 `chatId` 由调用侧通过 `ToolContext` 注入

这样可以避免模型伪造租户和用户边界

### Spring Bean 函数解析器：Function resolver

`SpringBeanToolCallbackResolver` 可以按 Bean 名解析函数工具

`ToolReferenceConfig` 中 `functionBean` 的 Bean 名就是工具名

`@Description` 成为工具描述

解析完成后仍然回到 `ToolCallback` 抽象，后续可以继续做 allowlist 和定义检查

参考接口：`/v1/tools/reference/function/bean/resolver`

### Function Tool Limitations

函数工具更适合显式构造工具契约，但也有边界

函数工具的主要限制如下：

- `FunctionToolCallback` 需要显式指定描述、输入类型和结果转换，否则模型看到的契约可能过弱
- `Supplier` 没有入参，适合只读固定能力，不适合需要用户上下文的业务查询
- `Consumer` 没有返回值，生产环境里通常意味着动作执行，必须特别谨慎
- `BiFunction<Input, ToolContext, Result>` 才适合同时读取模型参数和服务端上下文
- Spring Bean 函数解析器依赖 Bean 名作为工具名，Bean 命名一旦进入工具协议就不能随意改

函数工具看起来比方法工具“干净”，但不能因此绕过工具治理。只要函数会访问业务数据、外部系统或写状态，就必须进入请求级显式链路

## 六、Tool Specification

`Tool Specification` 是工具进入模型前必须稳定下来的契约层

模型不会理解 Java 类结构，它只接收 `ToolDefinition` 中的 `name`、`description` 和 `inputSchema`

### 工具名称

`MetaToolNames` 集中维护工具名称，避免 Controller、allowlist、测试和 `ToolDefinition` 之间出现字符串漂移

名称是模型调用工具的协议字段，不应因为类名重构而随意变化

新工具必须先定义稳定名称，再进入具体工具实现

### 工具分类

`MetaToolCategory` 用于区分基础工具、方法工具、函数工具、契约示例和执行示例

分类不是 Spring AI 必需字段，而是项目内部治理工具边界的辅助信息

后续如果接入工具市场、插件系统或权限面板，可以继续沿用这些分类

### 工具回调：Tool Callback

`ToolCallback` 是 Spring AI 工具调用的统一抽象

方法工具、函数工具、Spring Bean resolver 解析出来的工具最终都会收敛到 `ToolCallback`

### 工具定义：Tool Definition

`ToolDefinition` 描述模型实际看到的工具契约

它包含 `name`、`description` 和 `inputSchema`

模型不会看到 Java 方法体、record 类型、Function 对象或业务实现类

### JSON Schema

`inputSchema` 是模型生成工具参数的结构依据

声明式方法工具通常来自 `@ToolParam`

函数工具通常来自 `inputType(...)` 指定的入参类型，以及 `JsonClassDescription`、`JsonPropertyDescription`

schema 中只能出现模型应该生成的字段

租户、用户、会话、权限这类安全字段必须走 `ToolContext`

### 工具定义视图

`ToolCallbackDefinitionView` 从 `ToolCallback` 中提取 `ToolDefinition` 和 `ToolMetadata`

它用于参考接口返回 `name`、`description`、`inputSchema` 和 `returnDirect`

初学者应通过该视图理解模型看到的是工具契约，而不是 Java 代码

```
{
  "name": "functionCallback",
  "description": "通过 FunctionToolCallback 获取指定 IANA 时区的当前日期时间",
  "inputSchema": "{...}",
  "returnDirect": false
}
```

### 结果转换：Result Conversion

`ToolCallResultConverter` 负责把工具返回值转换为模型可读取的字符串

`CustomToolCallResultConverter` 是自定义转换器实现

`ToolResultConversionSupport` 是本项目统一创建结果转换器的入口

结果转换属于 Tool Specification，因为它影响模型后续看到的工具结果表达

### 工具上下文：Tool Context

`ToolContext` 是调用侧注入给工具执行期读取的上下文

它不会进入 `ToolDefinition.inputSchema`

`MetaToolContextKeys` 和 `ToolContextAccessor` 放在 `com.metax.tool.context`

该包单独存在，是为了把安全上下文字段和模型可见 schema 明确隔离

### 直接返回语义：returnDirect

`ToolSpecificationSupport.returnDirectDateTimeCallback` 演示 `ToolMetadata.returnDirect(true)`

`returnDirect=false` 时，工具结果会交回模型继续生成最终回答

`returnDirect=true` 时，工具执行结果直接返回调用方，不再交回模型继续回答

这适合下载链接、精确查询结果、确定性命令结果等短路场景

它不适合需要模型解释、总结或融合上下文的普通问答场景

### 重复工具名校验

`ToolSpecificationSupport.validateUniqueToolNames` 调用 `ToolCallingChatOptions.validateToolCallbacks`

重复工具名会让模型侧工具协议冲突，Spring AI 会拒绝这种配置

所有工具进入模型前都应先检查名称唯一性

## 七、Tool Execution

工具执行是模型提出工具调用之后，应用真正执行工具并处理结果的阶段

当前项目同时展示三条执行路径

### Framework-Controlled Tool Execution

`internalToolExecutionEnabled=true` 表示允许 ChatModel / Spring AI 内部工具执行链路处理工具调用

不显式设置时，Spring AI 默认允许内部工具执行

`ToolCallingOptionsFactory.frameworkControlledOptions` 保持默认内部执行语义

这条路径适合简单场景，但项目中更推荐用 `ToolCallAdvisor` 把工具执行纳入 advisor 链，方便统一观察和治理

### User-Controlled Tool Execution

`ToolCallingOptionsFactory.userControlledOptions` 设置 `internalToolExecutionEnabled=false`

该路径让 `ChatModel` 只返回工具调用请求，不自动执行工具

调用方需要自己解析工具调用、执行工具、处理结果并决定是否继续调用模型

它适合需要人工审批、审计、限流、事务编排或跨系统补偿的底层场景

```
ToolCallingChatOptions options = ToolCallingChatOptions.builder()
        .toolCallbacks(callbacks)
        .toolContext(toolContext)
        .internalToolExecutionEnabled(false)
        .build();
```

### Advisor-Controlled Tool Execution with ToolCallAdvisor

`MetaToolConfig` 构造 `ToolCallAdvisor` 并绑定 `ToolCallingManager`

`ToolCallingOptionsFactory.advisorControlledOptions` 用于表达这条路径的工具选项

`ToolCallAdvisor` 会在 advisor 链中复制 `ToolCallingChatOptions` 并关闭内部工具执行，由 advisor 链接管工具调用循环

工具调用结果如果 `returnDirect=false`，会作为下一轮消息交回模型继续生成最终回答

工具调用结果如果 `returnDirect=true`，会直接返回应用侧

`ToolCallAdvisor` 的顺序应靠近 `HIGHEST_PRECEDENCE`，让工具调用在请求处理阶段尽早进入，在响应处理阶段最后退出

`suppressToolCallStreaming` 可以过滤流式场景中的中间工具调用响应，只向下游暴露最终回答

### 默认工具路径：ChatClient defaultTools

`ChatClientFactory.buildChatClient` 和 `ChatClientFactory.buildRagChatClient` 都会调用私有 `buildClient(..., true)`

`buildClient(..., true)` 统一通过 `defaultTools(dateTimeTools)` 挂载 `DateTimeTools`

该路径只适合无副作用、无权限边界、可默认暴露给模型的基础工具

工具对该 `ChatClient` 的所有请求默认可见

```
ChatClient.builder(model)
        .defaultAdvisors(
                MessageChatMemoryAdvisor.builder(chatMemory).build(),
                SimpleLoggerAdvisor.builder().build()
        )
        .defaultTools(dateTimeTools)
        .build();
```

`ChatClientFactory.buildRequestToolChatClient` 会调用 `buildClient(..., false)`

这条请求级工具链路刻意不挂载 `defaultTools`，避免业务工具绕过请求级 allowlist、权限上下文和审计策略

### 默认回调路径：ChatClient defaultToolCallbacks

`defaultTools` 适合直接挂载带 `@Tool` 方法的对象

`defaultToolCallbacks` 适合挂载已经构造好的 `ToolCallback`

`ToolReferenceConfig.defaultMethodToolChatClient` 通过 `defaultToolCallbacks(methodCallback)` 演示 `MethodToolCallback`
默认挂载路径

`ToolReferenceConfig.defaultFunctionToolChatClient` 通过 `defaultToolCallbacks(functionCallback)` 演示
`FunctionToolCallback` 默认挂载路径

这两个 ChatClient 只服务参考接口，用于帮助初学者区分“默认工具对象”和“默认工具回调”

生产系统仍然只建议把无副作用、无权限边界的基础工具放入默认链路

### 请求级显式工具路径

`ToolChatService` 使用 `toolCallbacks` 在单次请求中暴露工具

`ToolChatRequest.toolNames` 只允许在服务端 allowlist 中收窄工具范围

`ToolChatService` 同时传入 `ToolCallAdvisor` 和 `ToolContext`

参考业务接口：`/v1/chat/tools`

```
chatClient.prompt()
        .advisors(metaToolCallAdvisor)
        .toolCallbacks(toolCallbacks)
        .toolContext(Map.of(
                MetaToolContextKeys.TENANT_ID, scope.tenantId(),
                MetaToolContextKeys.USER_ID, scope.userId(),
                MetaToolContextKeys.CHAT_ID, chatId
        ))
        .user(msg)
        .call()
        .content();
```

## 八、结果转换与异常处理

### 工具结果转换器：ToolCallResultConverter

`ToolCallResultConverter` 负责把工具返回值转换为模型可读取的字符串

`CustomToolCallResultConverter` 是本项目的自定义结果转换示例，方法工具和函数工具都可以复用

`ToolResultConversionSupport` 负责统一创建可复用的结果转换器

它覆盖三条官方路径：

- 声明式方法工具：`@Tool(resultConverter = CustomToolCallResultConverter.class)`
- 编程式方法工具：`MethodToolCallback.builder().toolCallResultConverter(...)`
- 编程式函数工具：`FunctionToolCallback.builder().toolCallResultConverter(...)`

注意一个很容易踩坑的点：未设置自定义 converter 的 `MethodToolCallback` 对 `String` 返回值可能返回 JSON 字符串字面量，自定义
converter 可以保持纯文本返回

### 工具异常处理器：ToolExecutionExceptionProcessor

`ToolExecutionExceptionProcessor` 决定工具异常是反馈给模型还是抛给调用方

`ToolExceptionHandlingSupport` 提供三类策略：

- `modelFeedbackExceptionProcessor()`：把异常消息作为工具结果反馈给模型
- `throwingExceptionProcessor()`：直接抛出 `ToolExecutionException`
- `rethrowingExceptionProcessor(...)`：按异常类型白名单解包并重抛运行时异常

对无副作用基础工具，可以把异常转成可读文本让模型继续回答

对写操作、鉴权失败、资金或数据变更类工具，应倾向于抛出异常并由业务层处理

## 九、工具解析：Tool Resolution

`ToolCallbackResolver` 负责把 `toolName` 解析成 `ToolCallback`

`toolNames` 通常配合 `ToolCallbackResolver` 使用

### 静态工具解析器：StaticToolCallbackResolver

`StaticToolCallbackResolver` 是静态工具表解析器

它适合已经构造好的静态 `ToolCallback` 列表

未命中时返回 `null`

### 委托工具解析器：DelegatingToolCallbackResolver

`DelegatingToolCallbackResolver` 是组合解析器

它按顺序访问多个 resolver，前一个 resolver 未命中时才继续访问后一个 resolver

适合组合静态注册表和 Spring Bean resolver

### Spring Bean 工具解析器：SpringBeanToolCallbackResolver

`SpringBeanToolCallbackResolver` 是函数 Bean 路径的官方解析器

它需要 `ApplicationContext`，所以运行参考放在 agent 模块

### 本项目入口

`ToolResolverSupport` 演示静态解析器和委托解析器的通用创建方式

## 十、工具参数增强：Tool Argument Augmentation

`Tool Argument Augmentation` 用于在已有 `ToolCallback` 外层追加额外工具参数 schema

Spring AI 官方实现是 `AugmentedToolCallbackProvider`，它会包装已有 `ToolCallbackProvider` 或 `@Tool` 工具对象

`ToolArgumentSupport` 演示 `AugmentedToolCallbackProvider` 的官方用法

`ToolAuditArguments` 作为 record 定义增强参数字段

增强字段会追加到 `ToolDefinition.inputSchema`

Spring AI 当前 augmenter 重点处理字段追加，字段描述可能按默认描述生成

工具执行前，`AugmentedToolCallback` 会把增强参数转换成 record 并发送给 `argumentConsumer`

`removeExtraArgumentsAfterProcessing=true` 时，增强字段会在调用原始工具前从 JSON 入参中移除

该机制不同于 `ToolContext`。增强参数会进入模型可见 schema，因此更适合观察、补充模型可见参数或做教学演示。租户、用户、权限这类不能被模型伪造的字段仍应优先使用
`ToolContext`

## 十一、ToolContext 和安全边界

`ToolContext` 用于承载租户、用户、会话、审计标识等执行期上下文

`ToolContext` 不会作为工具入参 schema 发给模型，因此模型不能伪造这些字段

工具实现应优先从 `ToolContext` 读取 `tenantId`、`userId` 和 `chatId`，而不是让模型通过普通参数生成这些安全字段

agent 请求级链路通过 `ChatClient.toolContext` 注入上下文，工具执行时通过 `ToolContextAccessor` 读取

### 上下文字段统一命名

`MetaToolContextKeys` 统一维护 `tenantId`、`userId` 和 `chatId` 等上下文字段名

字段名集中管理可以避免工具、服务层和测试之间出现 key 漂移

### 上下文读取

`ToolContextAccessor` 提供字符串上下文读取方法

工具实现应通过该辅助类读取 `ToolContext`，避免到处手写 Map 类型转换和空值判断

`ToolContext` 缺失时工具应按业务风险选择回退、拒绝或抛错

### 上下文不进入 schema

`ToolContext` 不会进入 `ToolDefinition.inputSchema`

模型看不到 `tenantId`、`userId` 和 `chatId` 这些安全字段

调用侧必须在 `ChatClient.toolContext` 中注入这些字段

```
tenantId=t1
userId=u1
chatId=t1-u1-tool-s1
```

## 十二、Observability

官方 Tools 文档单独列出 Observability，是为了提醒工具调用不是黑盒。工具一旦接入模型，至少要能回答四个问题：

- 本轮请求暴露了哪些工具
- 模型实际选择了哪个工具
- 工具参数是否来自模型，还是来自服务端 `ToolContext`
- 工具结果是交回模型继续回答，还是通过 `returnDirect` 直接返回

当前项目没有在 `meta-ai-tool` 下单独创建 `observability` 包，这是刻意选择

观测不是一个独立工具抽象，而是贯穿 ChatClient、Advisor、参考接口和业务日志的运行期能力。过早建一个空的 `observability`
包，只会让初学者误以为 Spring AI 有一个必须实现的观测接口

本项目当前观测入口如下：

- 基础 ChatClient 和 RAG ChatClient 通过 `SimpleLoggerAdvisor` 记录请求链路
- 请求级工具链路通过 `ToolCallAdvisor` 接管工具调用循环
- `ToolReferenceService` 返回工具名称、工具定义视图和实现要点，便于直接观察模型可见契约
- `ToolCallbackDefinitionView` 展示 `ToolDefinition` 和 `ToolMetadata.returnDirect`
- `MetaToolRegistry` 收敛请求级工具 allowlist，便于后续增加工具选择日志和审计

后续如果接入 Micrometer、Tracing 或专用审计表，优先放在 agent 或公共观测模块，而不是放进 `meta-ai-tool`

`meta-ai-tool` 只提供可被观测的稳定工具抽象，不承担运行期日志采集和链路上报职责

## 十三、默认工具与请求级显式工具

基础 `ChatClient` 可以通过 `defaultTools` 挂载 `DateTimeTools` 这种默认安全基础工具

`ChatClientFactory.buildChatClient` 和 `ChatClientFactory.buildRagChatClient` 当前都会挂载默认安全基础工具

`ChatClientFactory.buildRequestToolChatClient` 当前不挂载默认安全基础工具，只固定 Memory 和 Logger

默认安全基础工具代码统一放在 `com.metax.tool.foundation`
不要把 RAG 检索工具、数据库查询工具、业务查询工具、业务写入工具或依赖权限上下文的工具放入该包

RAG `ChatClient` 可以复用基础无副作用工具，但 RAG 检索 Advisor 仍由请求侧动态追加

业务工具必须由调用侧通过 `ToolCallAdvisor`、`toolCallbacks` 和 `toolContext` 显式注入

`MetaToolRegistry` 负责服务端 allowlist，客户端传入的 `toolNames` 只能在 allowlist 内收窄，不能扩大工具权限

## 十四、实现关系图

### 工具生成关系

```
@Tool method
  -> ToolCallbacks.from
  -> ToolCallback
  -> ToolDefinition + ToolMetadata
  -> ChatClient.toolCallbacks

Java Method
  -> Method
  -> ToolDefinitions.builder
  -> MethodToolCallback
  -> ToolCallback

Function / Supplier / Consumer / BiFunction
  -> FunctionToolCallback.builder
  -> inputType / description / ToolMetadata
  -> ToolCallback

Spring Bean Function
  -> SpringBeanToolCallbackResolver.resolve(beanName)
  -> FunctionToolCallback
  -> ToolCallback
```

### 工具执行关系

```
user message
  -> ChatClient
  -> ToolDefinition list
  -> ChatModel
  -> tool call request(name + arguments)
  -> ToolCallingManager
  -> ToolCallbackResolver / registered ToolCallback
  -> ToolCallback.call(arguments, ToolContext)
  -> ToolCallResultConverter
  -> ToolExecutionExceptionProcessor
  -> returnDirect ? caller : ChatModel
```

### 项目模块实现关系

```
meta-ai-tool
  -> method                       官方 Methods as Tools
  -> function                     官方 Functions as Tools
  -> specification                官方 Tool Specification
  -> specification.conversion     官方 Result Conversion
  -> execution                    官方 Tool Execution
  -> context                      官方 Tool Context 的工程安全边界
  -> resolver                     官方 Tool Resolution
  -> argument                     官方 Tool Argument Augmentation
  -> catalog                      项目工具名称和分类治理
  -> foundation                   项目默认安全基础工具
  -> reference                    项目参考接口模型
  -> DateTimeTools
  -> DeclarativeDateTimeTools
  -> ProgrammaticMethodToolCallbackFactory
  -> FunctionToolCallbackFactory
  -> ToolSpecificationSupport
  -> ToolCallingOptionsFactory
  -> ToolExceptionHandlingSupport
  -> ToolContextAccessor
  -> ToolResolverSupport
  -> ToolArgumentSupport

meta-ai-agent
  -> MetaToolConfig
  -> MetaToolRegistry
  -> ToolChatService
  -> ToolReferenceService
  -> ToolReferenceController
  -> defaultMethodToolChatClient
  -> defaultFunctionToolChatClient
```

## 十五、参考接口与学习路径

### 方法工具参考接口

- `/v1/tools/reference/method/declarative/runtime` 演示 `ChatClient.tools`
- `/v1/tools/reference/method/declarative/callbacks` 演示 `ToolCallbacks.from`
- `/v1/tools/reference/method/programmatic/runtime` 演示 `MethodToolCallback` 请求级暴露
- `/v1/tools/reference/method/programmatic/default` 演示 `defaultMethodToolChatClient` 中的
  `defaultToolCallbacks(methodCallback)` 默认暴露

### 函数工具参考接口

- `/v1/tools/reference/function/programmatic/runtime` 演示 `FunctionToolCallback` 请求级暴露
- `/v1/tools/reference/function/programmatic/default` 演示 `defaultFunctionToolChatClient` 中的
  `defaultToolCallbacks(functionCallback)` 默认暴露
- `/v1/tools/reference/function/bean/resolver` 演示 `SpringBeanToolCallbackResolver`

### 契约与执行参考接口

- `/v1/tools/reference/specification` 演示 `ToolDefinition`、`ToolMetadata` 和 `returnDirect`
- `/v1/tools/reference/execution` 演示 framework、user、advisor 和 named tool 四类 `ToolCallingChatOptions`
- `/v1/tools/reference/definitions` 返回当前参考工具的 `ToolDefinition` 视图

### 命令行调用示例：curl

```
curl -X POST http://localhost:8008/v1/chat/tools \
  -H "Content-Type: application/json" \
  -d '{
    "chatId": "t1-u1-tool-s1",
    "tenantId": "t1",
    "userId": "u1",
    "msg": "现在几点",
    "toolNames": ["currentDateTime"]
  }'
```

```
curl "http://localhost:8008/v1/tools/reference/method/declarative/callbacks?prompt=现在几点"
curl "http://localhost:8008/v1/tools/reference/function/programmatic/runtime?prompt=UTC 当前时间"
curl "http://localhost:8008/v1/tools/reference/function/bean/resolver?prompt=Asia/Shanghai 当前时间"
curl "http://localhost:8008/v1/tools/reference/specification"
curl "http://localhost:8008/v1/tools/reference/execution"
curl "http://localhost:8008/v1/tools/reference/definitions"
```

## 十六、工具治理检查清单

1. 工具名称是否稳定且唯一
2. `description` 是否能让模型正确判断何时调用
3. `inputSchema` 是否只包含模型应该生成的字段
4. `tenantId` / `userId` / `chatId` 是否通过 `ToolContext` 注入
5. 工具是否有副作用
6. 有副作用工具是否进入了请求级 allowlist
7. 异常是反馈给模型还是抛给业务层
8. `returnDirect=false` 时是否允许模型继续加工工具结果
9. `returnDirect=true` 时是否确认可以短路模型回答
10. 默认工具是否只包含无副作用基础能力
11. 测试是否覆盖工具定义、执行结果和安全边界
