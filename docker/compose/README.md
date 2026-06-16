# MetaAI Docker Compose

本目录提供两套独立部署栈：

- `vllm`：使用 vLLM OpenAI 兼容服务，Chat 和 Embedding 拆成独立容器，便于分别控制 GPU、显存和模型版本
- `ollama`：使用 Ollama 本地模型服务，Chat 和 Embedding 共用一个 Ollama 服务，适合单机本地模型部署

两套栈都包含 `meta-ai-agent`、Redis Stack、Qdrant、RustFS、PaddleX OCR 和 live-asr

PostgreSQL 作为外部已存在数据库接入，不在这里编排

`meta-ai-agent` 镜像由手工或云效流水线构建，Compose 只负责消费镜像和挂载配置文件

## 启动步骤

进入目标栈目录，复制环境模板：

```bash
cp .env.example .env
```

vLLM 栈初始化应用配置文件：

```bash
mkdir -p .data/config
cp config-template/application.properties .data/config/application.properties
cp config-template/application-openai.properties .data/config/application-openai.properties
```

Ollama 栈初始化应用配置文件：

```bash
mkdir -p .data/config
cp config-template/application.properties .data/config/application.properties
cp config-template/application-ollama.properties .data/config/application-ollama.properties
```

按现场环境修改 `.env` 和 `.data/config/*.properties`，然后启动：

```bash
docker compose up -d
```

查看服务：

```bash
docker compose ps
```

停止服务：

```bash
docker compose down
```

## Ollama 模型初始化

Ollama 栈默认不执行 `ollama-init`，避免离线生产环境因为无法联网拉模型而阻塞应用启动

联网开发环境或首次联网初始化时，可以手动执行：

```bash
docker compose --profile init up ollama-init
```

也可以直接指定目标服务：

```bash
docker compose up ollama-init
```

直接指定 `ollama-init` 时，Docker Compose 会运行这个目标服务及其 `depends_on` 依赖服务，但这不等于激活整个 `init` profile

运维文档中推荐使用 `--profile init` 写法，语义更明确，也更容易看出这是一次性联网初始化任务

离线生产环境必须提前准备好 `.data/ollama` 模型目录，再拷贝到目标机器

如果 `.data/ollama` 已经包含 `OLLAMA_CHAT_MODEL` 和 `OLLAMA_EMBEDDING_MODEL` 对应模型，可以不执行 `ollama-init`

## 数据目录

所有运行期数据放在 Compose 文件同级 `.data` 下，并已通过根级 `.gitignore` 排除：

- `.data/redis`
- `.data/qdrant`
- `.data/rustfs`
- `.data/ollama`
- `.data/huggingface`
- `.data/config`
- `.data/logs`

`vllm` 栈使用 `.data/huggingface` 缓存 Hugging Face 模型

`ollama` 栈使用 `.data/ollama` 保存 Ollama 模型，不与 Hugging Face 缓存混用

live-asr 和 PaddleX OCR 镜像已经在构建期预置模型缓存，默认不挂载模型目录，避免空目录覆盖镜像内模型

不要在未创建 `.data/config/application.properties` 和对应 profile 配置文件的情况下启动 Compose。Docker 会把缺失的挂载源当目录创建，导致 Spring Boot 无法按文件加载配置

## 端口规划

两套栈统一使用 `18000 ~ 18010` 宿主机端口段

容器之间通过服务名和容器内端口访问，不使用宿主机端口。例如 Java 应用访问 vLLM Chat 使用 `http://vllm-chat:8000`，访问
Ollama 使用 `http://ollama:11434`

| 端口      | vLLM 栈                    | Ollama 栈                |
|---------|---------------------------|-------------------------|
| `18000` | MetaAI API                | MetaAI API              |
| `18001` | vLLM Chat OpenAI API      | Ollama HTTP API         |
| `18002` | vLLM Embedding OpenAI API | 预留，不占用                  |
| `18003` | Redis 协议端口                | Redis 协议端口              |
| `18004` | Redis Stack Browser       | Redis Stack Browser     |
| `18005` | Qdrant HTTP / Dashboard   | Qdrant HTTP / Dashboard |
| `18006` | Qdrant gRPC               | Qdrant gRPC             |
| `18007` | RustFS S3 API             | RustFS S3 API           |
| `18008` | RustFS Console            | RustFS Console          |
| `18009` | PaddleX OCR HTTP          | PaddleX OCR HTTP        |
| `18010` | live-asr WebSocket        | live-asr WebSocket      |

## 验证入口

通用入口：

- MetaAI API：`http://localhost:18000/v3/api-docs`
- Redis Stack Browser：`http://localhost:18004/redis-stack/browser`
- Qdrant Collections：`http://localhost:18005/collections`
- Qdrant Dashboard：`http://localhost:18005/dashboard`
- RustFS Console：`http://localhost:18008/rustfs/console/browser`
- PaddleX OCR：`http://localhost:18009/health`
- live-asr WebSocket：`ws://localhost:18010`

vLLM 栈入口：

- vLLM Chat：`http://localhost:18001/v1/models`
- vLLM Embedding：`http://localhost:18002/v1/models`

Ollama 栈入口：

- Ollama 模型列表：`http://localhost:18001/api/tags`

## 应用镜像

应用镜像可以手工构建：

```bash
docker build -f meta-ai-agent/Dockerfile -t registry.cn-hangzhou.aliyuncs.com/metax/meta-ai-agent:latest .
```

该命令需要在仓库根目录执行。云效流水线也使用同一个 Dockerfile。仓库根目录当前没有 `settings.xml`，如需 Maven 私服配置，应由流水线注入 Maven settings

## 配置一致性

vLLM / Ollama 服务启动模型名在 `.env` 中配置，Java 应用调用模型名在 `.data/config/application-openai.properties` 或
`.data/config/application-ollama.properties` 中配置

修改模型名、端口、对象存储密钥、Redis 密码或向量库类型时，两边涉及同一服务的配置必须同步

切换 `spring.ai.vectorstore.type` 或 embedding 模型后，必须重建对应向量索引，不能复用旧 embedding 数据

vLLM Embedding 模型 revision 变更后，也必须重建向量索引
