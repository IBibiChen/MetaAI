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

## Nginx 网关配置

MetaAI 前端已经打入 `meta-ai-agent` 可执行 jar，生产网关需要同时暴露页面路径和 API 路径：

- 页面入口：`/meta-ai/`
- iframe 嵌入入口：`/meta-ai/embed/chat?tenantId=t1&userId=u1&kbId=kb1`
- API 入口：`/api/meta-ai/v1/**`
- Spring Boot 内部实际接收路径：`/v1/**`

下面示例假设网关可以通过服务名访问 `service-meta-ai`。如果网关部署在 Compose 外部，也可以把 `proxy_pass` 替换为
`http://127.0.0.1:18000`

SSE 流式接口的 location 必须放在普通 API location 前面，避免被 `/api/meta-ai/` 提前匹配

Spring Boot 只为明确的前端 History 路由配置 `index.html` 回退，例如 `/chat`、`/documents` 和 `/embed/chat`。不要在后端使用
`/{path:[^\\.]*}` 或 `/**/{path:[^\\.]*}` 这类全局通配回退，否则写错的 `/v1/**`、`/internal/**` 或文档接口路径可能被误转成前端页面，破坏
API 404 语义

```nginx
# MetaAI 前端页面
location /meta-ai/ {
    proxy_pass http://service-meta-ai/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}

# MetaAI 普通聊天 SSE
location /api/meta-ai/v1/chat {
    rewrite ^/api/meta-ai/(.*)$ /$1 break;
    proxy_pass http://service-meta-ai;
    proxy_http_version 1.1;
    proxy_buffering off;
    proxy_cache off;
    proxy_set_header Connection "";
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}

# MetaAI RAG 聊天 SSE
location /api/meta-ai/v1/rag {
    rewrite ^/api/meta-ai/(.*)$ /$1 break;
    proxy_pass http://service-meta-ai;
    proxy_http_version 1.1;
    proxy_buffering off;
    proxy_cache off;
    proxy_set_header Connection "";
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}

# MetaAI 其他 API
location /api/meta-ai/ {
    rewrite ^/api/meta-ai/(.*)$ /$1 break;
    proxy_pass http://service-meta-ai;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

## 应用镜像

应用镜像可以手工构建：

```bash
docker build -f meta-ai-agent/Dockerfile -t registry.cn-hangzhou.aliyuncs.com/metax/meta-ai-agent:latest .
```

该命令需要在仓库根目录执行。云效流水线也使用同一个 Dockerfile。仓库根目录当前没有 `settings.xml`，如需 Maven 私服配置，应由流水线注入 Maven settings

本地直接执行 Maven 打包时会调用 PATH 中的 `npm install` 和 `npm run build`，因此本机需要先安装 Node / npm。当前建议使用
Node 22 和 npm 10，与 Dockerfile 的 Node 构建阶段保持同一主版本

Docker 镜像构建不会在 Maven 阶段再次执行 npm。Dockerfile 会先用 Node 阶段执行 `npm ci` 和 `npm run build`，再通过
`-Dmeta-ai.console.skip=true` 让 Maven 只复制已有 `dist` 并打包 Spring Boot jar

Dockerfile 会显式使用 `-Pdocker`。该 Maven profile 会排除 `meta-ai-agent/src/main/resources/application*.properties`，
避免把本地模型地址、Token、Redis 密码等环境配置打进镜像

容器运行时必须通过外部配置文件或环境变量提供 Spring Boot 配置。推荐把配置挂载到 `/app/config/`，让 Spring Boot
默认外部配置优先级自动读取，例如 `/app/config/application.properties`

本地 Maven 打包默认使用 `dev` profile，会保留 `application*.properties`。这样 `java -jar` 快速验证时不需要额外挂载配置文件

`spring-boot-maven-plugin` 当前不配置 `<classifier>exec</classifier>`。Spring Boot 默认会把主产物
`meta-ai-agent-1.0.0.jar` 重打包为可执行 jar，并把原始普通 jar 备份为 `meta-ai-agent-1.0.0.jar.original`

如果配置 `<classifier>exec</classifier>`，插件会保留 `meta-ai-agent-1.0.0.jar` 作为普通 jar，并额外生成
`meta-ai-agent-1.0.0-exec.jar` 作为可执行 jar。这个模式适合同时发布普通依赖包和可执行包的模块；`meta-ai-agent`
是应用入口模块，部署只需要一个可执行主产物，因此不使用该配置

本地 Maven 打包命令：

```bash
mvn -pl :meta-ai-agent -am package -DskipTests
```

如果前端 `dist` 已经存在，只想跳过 npm 并重新打后端 jar，可以显式跳过前端构建：

```bash
mvn -pl :meta-ai-agent -am package -DskipTests -Dmeta-ai.console.skip=true
```

本地直接 `java -jar` 验证时，前端需要使用 `.env.boot` 生成根路径版本的 `dist`，否则生产构建中的 `/meta-ai/assets/**`
或开发代理中的 `/api/v1/**` 会绕过 Nginx / Vite 直接请求后端，导致 404：

```bash
cd meta-ai-console
npm run build:boot
cd ..
mvn -pl :meta-ai-agent -am clean package -DskipTests -Dmeta-ai.console.skip=true
java -jar meta-ai-agent/target/meta-ai-agent-1.0.0.jar
```

本地默认 `dev` profile 会把 `application*.properties` 打进 jar。只有 Docker 镜像构建或显式执行 `-Pdocker` 时才会排除这些配置文件

PowerShell 中带点号的 Maven 属性建议加引号，例如 `"-Dmeta-ai.console.skip=true"`。不加引号时，某些环境会把后半段误解析成
Maven lifecycle phase。Docker profile 可以直接写 `-Pdocker`

当前构建约定：

- `exec-maven-plugin` 只负责调用本机 PATH 中的 `npm`，不托管 Node / npm 版本
- `maven-resources-plugin` 只负责把 `meta-ai-console/dist` 复制到 `target/classes/static`
- `-pl :meta-ai-agent` 按 artifactId 选择应用入口模块，不依赖目录名
- `-am` 会同时构建 `meta-ai-agent` 依赖的 reactor 模块，不会漏掉运行所需模块
- 默认 `dev` profile 保留后端配置文件，`docker` profile 排除 `application*.properties`
- Dockerfile 的 Node 阶段负责镜像内的前端可复现构建，Maven 阶段只负责打 Spring Boot jar
- 如果团队以后要求 Maven 自动下载并锁定 Node / npm，再考虑切换到 `frontend-maven-plugin`

## 配置一致性

vLLM / Ollama 服务启动模型名在 `.env` 中配置，Java 应用调用模型名在 `.data/config/application-openai.properties` 或
`.data/config/application-ollama.properties` 中配置

修改模型名、端口、对象存储密钥、Redis 密码或向量库类型时，两边涉及同一服务的配置必须同步

切换 `spring.ai.vectorstore.type` 或 embedding 模型后，必须重建对应向量索引，不能复用旧 embedding 数据

vLLM Embedding 模型 revision 变更后，也必须重建向量索引
