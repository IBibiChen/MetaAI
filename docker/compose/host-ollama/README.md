# MetaAI Host Ollama 部署栈

本目录用于宿主机已经安装 Ollama 的本地服务器部署场景

该栈不启动 Ollama 容器，Chat 模型和 Embedding 模型直接访问宿主机 Ollama
Redis Stack、Qdrant、RustFS、PaddleX OCR、live-asr 和 meta-ai-agent 仍使用 Docker Compose 编排

## 架构边界

- LLM / Embedding：宿主机 Ollama，默认 `http://host.docker.internal:11434`
- Java 应用：`meta-ai-agent` 容器
- ChatMemory / Redis VectorStore：Redis Stack 容器
- 可选向量库：Qdrant 容器
- 对象存储：RustFS 容器
- OCR：PaddleX OCR 容器
- 实时语音识别：live-asr 容器

不要在该目录中额外启动 Ollama 容器，否则会出现模型缓存、端口和显存重复占用

## 宿主机 Ollama 准备

确认宿主机 Ollama 可用：

```bash
ollama list
curl http://127.0.0.1:11434/api/tags
```

拉取模型，模型名按现场实际替换：

```bash
ollama pull qwen3.6:35b
ollama pull bge-m3
```

Linux Docker 容器访问宿主机服务时，Ollama 不能只监听 `127.0.0.1`

建议配置 systemd：

```bash
sudo systemctl edit ollama
```

写入：

```ini
[Service]
Environment="OLLAMA_HOST=0.0.0.0:11434"
Environment="OLLAMA_NO_CLOUD=1"
Environment="OLLAMA_KEEP_ALIVE=10m"
Environment="OLLAMA_CONTEXT_LENGTH=8192"
Environment="OLLAMA_MAX_LOADED_MODELS=2"
Environment="OLLAMA_NUM_PARALLEL=1"
```

重启：

```bash
sudo systemctl daemon-reload
sudo systemctl restart ollama
sudo systemctl status ollama --no-pager
```

验证容器可以访问宿主机 Ollama：

```bash
docker run --rm --add-host=host.docker.internal:host-gateway curlimages/curl:latest \
  curl -fsS http://host.docker.internal:11434/api/tags
```

如果这里失败，先修 Ollama 监听地址、防火墙或 Docker host-gateway，不要继续启动应用

## 本地构建应用镜像

在仓库根目录执行：

```bash
docker build -f meta-ai-agent/Dockerfile -t meta-ai-agent:local .
```

检查镜像：

```bash
docker image inspect meta-ai-agent:local
```

如果在开发机打包并部署到另一台服务器：

```bash
docker save meta-ai-agent:local -o meta-ai-agent-local.tar
scp meta-ai-agent-local.tar user@SERVER_IP:/opt/meta-ai/
ssh user@SERVER_IP
cd /opt/meta-ai
docker load -i meta-ai-agent-local.tar
```

## 初始化部署目录

建议部署目录：

```bash
sudo mkdir -p /opt/meta-ai/host-ollama
sudo chown -R "$USER:$USER" /opt/meta-ai
```

复制本目录内容到服务器部署目录：

```bash
cp -r docker/compose/host-ollama/* /opt/meta-ai/host-ollama/
cp docker/compose/host-ollama/.env.example /opt/meta-ai/host-ollama/.env
cd /opt/meta-ai/host-ollama
```

初始化数据目录和配置：

```bash
mkdir -p .data/config .data/prompts .data/logs .data/redis .data/qdrant .data/rustfs
cp config-template/application.properties .data/config/application.properties
cp config-template/application-ollama.properties .data/config/application-ollama.properties
cp -r /path/to/MetaAI/meta-ai-agent/src/main/resources/prompts/* .data/prompts/
```

按现场环境修改：

```bash
vi .env
vi .data/config/application.properties
vi .data/config/application-ollama.properties
```

必须重点核对：

- `.env` 中 `META_AI_IMAGE`
- Redis 密码：`.env` 与 `application.properties` 必须一致
- RustFS 密钥：`.env` 与 `application.properties` 必须一致
- PostgreSQL 地址、用户名、密码
- `application-ollama.properties` 中 Chat 模型和 Embedding 模型名称
- `spring.ai.vectorstore.type` 使用 `redis` 还是 `qdrant`

## 启动

拉取基础设施镜像：

```bash
docker compose pull redis-stack qdrant rustfs paddlex-ocr live-asr
```

启动：

```bash
docker compose up -d
```

查看状态：

```bash
docker compose ps
docker compose logs -f meta-ai-agent
```

## 验证

验证 MetaAI API：

```bash
curl http://127.0.0.1:18000/v3/api-docs
```

验证容器网络访问宿主机 Ollama：

```bash
docker run --rm --network meta-ai-host-ollama_default --add-host=host.docker.internal:host-gateway \
  curlimages/curl:latest curl -fsS http://host.docker.internal:11434/api/tags
```

验证 Redis Stack：

```bash
docker compose exec redis-stack redis-cli -a "$REDIS_PASSWORD" ping
docker compose exec redis-stack redis-cli -a "$REDIS_PASSWORD" FT._LIST
```

验证 Qdrant：

```bash
curl http://127.0.0.1:18005/collections
```

验证 RustFS：

```bash
curl http://127.0.0.1:18007/health
```

验证 OCR：

```bash
curl http://127.0.0.1:18009/health
```

查看 ASR 日志：

```bash
docker compose logs --tail=100 live-asr
```

## 运维命令

只重启应用：

```bash
docker compose up -d --no-deps --force-recreate meta-ai-agent
```

修改 prompt 后重启应用：

```bash
docker compose restart meta-ai-agent
```

查看日志：

```bash
docker compose logs -f meta-ai-agent
docker compose logs -f redis-stack
docker compose logs -f qdrant
docker compose logs -f rustfs
docker compose logs -f paddlex-ocr
docker compose logs -f live-asr
```

停机但保留数据：

```bash
docker compose down
```

## 注意事项

切换 Embedding 模型后必须重建向量库索引并重新入库，否则向量维度或语义空间不一致，会直接破坏检索质量

宿主机 Ollama、容器 Redis Stack、Qdrant、RustFS 的数据分别独立持久化，不要把 Ollama 模型目录放进本 Compose 的 `.data`

生产环境应替换默认 token、Redis 密码、RustFS 密钥和数据库密码
