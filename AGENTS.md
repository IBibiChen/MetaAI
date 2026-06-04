# 仓库指南

## 项目结构与模块组织

本仓库是 Java 17 + Spring Boot + Spring AI 的 Maven 多模块项目，父工程负责统一版本和模块聚合。`meta-ai-agent` 是应用入口模块，包含
REST 接口、配置类、业务服务、Prompt 模板和 `MetaAIApplication`。`meta-ai-rag` 承载 RAG、ETL、索引、检索、文档读取和对象存储抽象。
`meta-ai-chat` 与 `meta-ai-common` 当前主要是模块骨架，新增代码前先确认是否已有更合适的职责边界。

源码位于各模块的 `src/main/java`，测试位于 `src/test/java`，资源和 Prompt 位于 `src/main/resources`，例如
`meta-ai-agent/src/main/resources/prompts`。

## 构建、测试与本地运行

仓库没有 Maven Wrapper，使用本机 `mvn`。

```bash
mvn clean install
mvn test
mvn test -pl meta-ai-agent
mvn test -pl meta-ai-rag
mvn spring-boot:run -pl meta-ai-agent
```

`mvn clean install` 执行全量构建和测试。`-pl` 用于限定模块，适合局部验证。运行应用前确认本地模型、Redis、数据库和必要环境变量已配置，敏感信息不要写入仓库。

## 编码风格与命名约定

代码使用 Java 17、UTF-8 和 Maven 父 POM 统一依赖版本，子模块依赖优先不写版本号。类名使用 `UpperCamelCase`，方法和字段使用
`lowerCamelCase`，测试类以 `*Test` 结尾。配置类中的 `@Bean` 方法必须保留 JavaDoc，说明 Bean 用途、适用场景和关键绑定关系。

中文注释遵守中英文数字间留空格、中文标点、自然语言行尾不加标点等全局规则。不要为了“简洁”删除现有排查记录、命令示例或历史说明，除非能证明信息已经失效。

## 测试规范

测试基于 Spring Boot Test 与 JUnit。新增服务、控制器、配置绑定、RAG 检索、ETL 管线或存储逻辑时，应补充对应单元测试或切片测试。优先运行受影响模块测试，再视变更范围执行全量
`mvn test`。

## 提交与 Pull Request 规范

提交历史使用类似 `feat(MetaAI): Retrieval` 的格式。推荐格式为 `type(MetaAI): 简短主题`，例如 `fix(MetaAI): 修复检索过滤条件`
。主题应说明行为变化，不要只写“update”。

Pull Request 应包含变更摘要、测试结果、相关问题链接；涉及接口、配置或可视化输出时，附上示例请求、响应或截图。跨模块变更需要说明影响范围和回滚方式。

## 安全与配置提示

不要提交 API Key、数据库密码、Redis 密码或本地私有地址。配置优先通过环境变量、本地配置文件或部署平台注入。涉及 Spring AI
版本、starter 名称、Advisor 或模型配置时，优先核对官方文档，避免使用过期资料。
