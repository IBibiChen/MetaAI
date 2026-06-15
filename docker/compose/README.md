# MetaAI Docker Compose

本目录提供两套独立部署栈：

- `vllm`：面向 4 卡大机器，使用 vLLM OpenAI 兼容服务
- `ollama`：面向 1 卡工作站，使用 Ollama 本地模型服务

两套栈都包含 `meta-ai-agent`、Redis Stack、Qdrant、RustFS、PaddleX OCR 和 live-asr。PostgreSQL 作为外部已存在数据库接入，不在这里编排

`meta-ai-agent` 镜像由手工或云效流水线构建，Compose 只负责消费镜像和挂载配置文件

## 启动步骤

进入目标栈目录，复制环境模板：

```powershell
Copy-Item .env.example .env
```

vLLM 栈初始化应用配置文件：

```powershell
New-Item -ItemType Directory -Force .data\config
Copy-Item config-template\application.properties .data\config\application.properties
Copy-Item config-template\application-openai.properties .data\config\application-openai.properties
```

Ollama 栈初始化应用配置文件：

```powershell
New-Item -ItemType Directory -Force .data\config
Copy-Item config-template\application.properties .data\config\application.properties
Copy-Item config-template\application-ollama.properties .data\config\application-ollama.properties
```

按现场环境修改 `.env` 和 `.data/config/*.properties`，然后启动：

```powershell
docker compose up -d
```

查看服务：

```powershell
docker compose ps
```

停止服务：

```powershell
docker compose down
```

## 数据目录

所有运行期数据放在 Compose 文件同级 `.data` 下，并已通过根级 `.gitignore` 排除：

- `.data/redis`
- `.data/qdrant`
- `.data/rustfs`
- `.data/ollama`
- `.data/huggingface`
- `.data/config`
- `.data/logs`

live-asr 和 PaddleX OCR 镜像已经在构建期预置模型缓存，默认不挂载模型目录，避免空目录覆盖镜像内模型

不要在未创建 `.data/config/application.properties` 和对应 profile 配置文件的情况下启动 Compose。Docker 会把缺失的挂载源当目录创建，导致 Spring Boot 无法按文件加载配置

## 应用镜像

应用镜像可以手工构建：

```powershell
docker build -f meta-ai-agent\Dockerfile -t registry.cn-hangzhou.aliyuncs.com/metax/meta-ai-agent:latest .
```

该命令需要在仓库根目录执行。云效流水线也使用同一个 Dockerfile。仓库根目录当前没有 `settings.xml`，如需 Maven 私服配置，应由流水线注入 Maven settings

## 配置一致性

vLLM / Ollama 服务启动模型名在 `.env` 中配置，Java 应用调用模型名在 `.data/config/application-openai.properties` 或 `.data/config/application-ollama.properties` 中配置。修改模型名、端口、对象存储密钥、Redis 密码或向量库类型时，两边涉及同一服务的配置必须同步

## 验证入口

- MetaAI API：`http://localhost:8008/v3/api-docs`
- Qdrant：`http://localhost:6333/collections`
- RustFS：`http://localhost:9001`
- PaddleX OCR：`http://localhost:18080/health`
- FunASR WebSocket：`ws://localhost:10096`

切换 `spring.ai.vectorstore.type` 或 embedding 模型后，必须重建对应向量索引，不能复用旧 embedding 数据
