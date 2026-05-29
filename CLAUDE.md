# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览

MetaAI 是一个基于 **Spring AI + Spring AI Alibaba** 的 AI 代理学习/实验项目，采用 Maven 多模块结构。

- **JDK**: 17
- **Spring Boot**: 3.5.9
- **Spring AI**: 1.1.2 (含 `spring-ai-alibaba` 1.1.2.0)
- **大模型**: 阿里云 DashScope (`qwen-plus` 对话、`text-embedding-v3` 嵌入) + 本地 Ollama (嵌入)
- **存储**: Redis Stack (对话记忆 + 向量库)

## 模块结构 (重要)

父 POM (`com.metax:MetaAI:1.0.0`) 声明 4 个子模块，但**当前只有 `meta-ai-agent` 有实际代码**：

| 模块 | 状态 |
| --- | --- |
| `meta-ai-agent` | 唯一实现模块 — Spring Boot 应用，端口 **8008** |
| `meta-ai-chat` | 空壳，仅 `pom.xml` |
| `meta-ai-common` | 空壳，仅 `pom.xml` |
| `meta-ai-rag` | 空壳，仅 `pom.xml` |

依赖版本统一在**父 POM 的 `dependencyManagement`** 中通过 BOM 管理 (spring-boot / spring-ai / spring-ai-alibaba)，子模块不写版本号。

## 常用命令

仓库**没有 Maven Wrapper (`mvnw`)**，需使用本机 `mvn`。

```bash
# 全量构建 (在仓库根目录)
mvn clean install

# 仅构建 agent 模块及其依赖
mvn clean install -pl meta-ai-agent -am

# 运行 agent 应用 (端口 8008)
mvn spring-boot:run -pl meta-ai-agent

# 运行 agent 模块全部测试
mvn test -pl meta-ai-agent

# 运行单个测试类 / 单个方法
mvn test -pl meta-ai-agent -Dtest=ChatControllerTest
mvn test -pl meta-ai-agent -Dtest=ChatControllerTest#client
```

### 启动前置依赖

应用启动依赖以下外部服务，缺失会导致 Bean 初始化失败：

- **环境变量** `DASH_SCOPE_API_KEY` — DashScope API 密钥 (见 `application.properties` 的 `spring.ai.dashscope.api-key`)
- **Redis Stack** — `localhost:6399`，密码 `123456` (对话记忆用 db 3，向量库需 RediSearch 模块)
- **Ollama** — `http://localhost:11434` (仅本地嵌入模型用到)

### 验证接口

```bash
curl "http://localhost:8008/v1/client/chat?msg=你好"   # 经 ChatClient (带记忆 + 日志 Advisor)
curl "http://localhost:8008/v1/model/chat?msg=你好"     # 直连 ChatModel (无记忆)
```

## 架构要点

所有代码在 `meta-ai-agent`，包根 `com.metax`，入口 `MetaAIApplication`。核心是 `config/` 下的一组手写 `@Bean` 装配 (而非纯自动配置)：

- **`ChatClientConfig`** — 全局 `ChatClient`，设定系统提示词 (海盗口吻) 并按链装配 Advisor。
  - **Advisor 顺序敏感**：请求方向 `Memory → RAG → SafeGuard → Logger → Model`，响应方向相反。新增 Advisor 务必注意插入位置，`SimpleLoggerAdvisor` 应放在链末端。
- **`ChatMemoryConfig`** — `MessageWindowChatMemory` (保留最近 30 条) + `RedissonRedisChatMemoryRepository` (db 3，keyPrefix `chat:memory:meta`)。
  - **会话键约定**：`tenantId:userId:sessionId`，调用时通过 `spec.param(ChatMemory.CONVERSATION_ID, ...)` 传入 (见 `ChatController#client`)。
- **`DashScopeConfig`** — 由 api-key 构造 `DashScopeApi` Bean，供嵌入模型等复用。
- **`EmbeddingModelConfig`** — 同时注册两个 `EmbeddingModel`：`dashScopeEmbeddingModel` 与 `ollamaLocalEmbeddingModel`。**注入时必须按 Bean 名区分** (字段名即限定名)，否则歧义注入失败。
- **`RedisConfig`** — 通用 `RedisTemplate`，String key + `GenericJackson2JsonRedisSerializer` value。
- **`VectorStoreConfig`** — 当前为空占位 (RAG 尚未接入，`QuestionAnswerAdvisor` 在 `ChatClientConfig` 中被注释)。

`application.properties` 中大量配置项 (OpenAI starter、`QuestionAnswerAdvisor`、内存版 `ChatMemoryRepository`) 以注释形式保留为可切换的备选实现。

## 代码规范

遵循全局 `~/.claude/CLAUDE.md` 的中文沟通与注释格式规范。本仓库现有代码的既定约定：

- 每个类带 JavaDoc 头：`@author IBibiChen`、`@version v1.0`、`@since` 日期。
- 注释中英文/数字间留空格，使用英文标点 + 空格。
