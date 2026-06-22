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
docker tag meta-ai-agent:local registry.cn-hangzhou.aliyuncs.com/metax/meta-ai-agent:2026-06-18
docker push registry.cn-hangzhou.aliyuncs.com/metax/meta-ai-agent:2026-06-18
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

RustFS 镜像默认使用 `10001:10001` 运行，宿主机 bind mount 目录必须允许该用户写入：

```bash
sudo chown -R 10001:10001 .data/rustfs
sudo chmod -R u+rwX,g+rwX .data/rustfs
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

## 第三方文档同步

MetaAI 侧配置写在服务器部署目录的 `.data/config/application.properties`

首次部署时该文件通常由 `config-template/application.properties` 复制生成，如果服务器上已经存在
`.data/config/application.properties`，需要手动把下面配置同步过去

```properties
# 第三方系统文档同步适配器
# 首次联调建议只开启通知入队接口，确认老系统能推送文件 ID 后再开启 Worker 和补偿扫描
metax.external-adapter.enabled=true
metax.external-adapter.tenant-id=t1
metax.external-adapter.kb-id=kb1
metax.external-adapter.max-attempts=3

# 后台 Worker 负责下载、对象存储归档、提交索引和等待索引终态
# 已确认老系统能推送文件 ID 后开启 Worker，继续验证下载、归档、索引和状态回写链路
metax.external-adapter.worker.enabled=true
metax.external-adapter.worker.idle-interval=5s
metax.external-adapter.worker.lock-timeout=30m
metax.external-adapter.worker.index-timeout=30m
metax.external-adapter.worker.index-poll-initial-interval=2s
metax.external-adapter.worker.index-poll-max-interval=10s
metax.external-adapter.worker.index-poll-multiplier=1.5

# 第三方老系统独立文件服务
# 对应老系统 fsip.config.host，Docker 内优先通过 host.docker.internal 访问宿主机文件服务
metax.external-adapter.file-service.host=http://host.docker.internal:10086
metax.external-adapter.file-service.download-url=/v1/download/
metax.external-adapter.file-service.authorization=123010068
metax.external-adapter.file-service.timeout=5m

# 调用 MetaAI 自身对象存储文档接口
metax.external-adapter.storage-api.base-url=http://meta-ai-agent:8008
metax.external-adapter.storage-api.auto-index=true
metax.external-adapter.storage-api.upload-timeout=10m

# 补偿扫描会定时扫描历史漏通知文件，确认链路稳定后再开启
metax.external-adapter.reconcile.enabled=false
metax.external-adapter.reconcile.batch-size=500
metax.external-adapter.reconcile.fixed-delay-millis=300000
```

老系统侧配置由业务系统配置文件维护，MetaAI 仓库不直接修改老系统配置

老系统文件服务配置通常类似：

```yaml
fsip:
  config:
    authorization: 123010068
    host: http://172.17.0.1:10086
    uploadUrl: /v1/upload/
    downloadUrl: /v1/download/
    mkdirUrl: /v1/mkdir/
    downPrefix: http://172.17.0.1:10086/v1/download/
    getFilePrefix: http://172.17.0.1:10086/v1/download/
```

MetaAI 容器内访问该独立文件服务时，优先使用 `http://host.docker.internal:10086`

当前 `docker-compose-o.yaml` 已给 `meta-ai-agent` 配置 `host.docker.internal:host-gateway`，比直接写死 `172.17.0.1` 更稳

老系统容器和 `meta-ai-agent` 在同一个 Compose `default` 网络内时，老系统访问 MetaAI 必须使用服务名，不能写 `localhost`

```yaml
meta-ai:
  external-sync:
    enabled: true
    base-url: http://meta-ai-agent:8008
    read-timeout: 10000
```

注意：如果现场 Docker 环境中 `host.docker.internal` 不通，可以临时把 `metax.external-adapter.file-service.host` 改为
`http://172.17.0.1:10086` 验证，但不要把它当成跨环境稳定地址

如果还没有确认老系统能成功调用 MetaAI 入队接口，建议保持：

```properties
metax.external-adapter.worker.enabled=false
metax.external-adapter.reconcile.enabled=false
```

当前如果已经确认老系统能成功推送文件 ID，下一步开启 Worker：

```properties
metax.external-adapter.worker.enabled=true
```

如需补偿扫描历史漏通知文件，再开启：

```properties
metax.external-adapter.reconcile.enabled=true
```

使用 `docker-compose-o.yaml` 部署时，修改配置后重启业务 Java 和 MetaAI：

```bash
docker compose -f docker-compose-o.yaml up -d --no-deps --force-recreate meta-ai-agent java
```

验证老系统容器能访问 MetaAI 入队接口：

```bash
docker compose -f docker-compose-o.yaml exec java sh -c \
  "wget -S -O- --header='Content-Type: application/json' --post-data='{\"externalFileIds\":[\"实际文件ID\"]}' http://meta-ai-agent:8008/internal/external/documents/sync"
```

验证 MetaAI 容器能访问独立文件服务：

```bash
docker compose -f docker-compose-o.yaml exec meta-ai-agent sh -c \
  "wget -S -O- --header='Authorization: 123010068' http://host.docker.internal:10086/v1/download/实际文件ID"
```

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

当前业务系统网关访问 MetaAI 前端时，页面入口通常走老系统现有 HTTP 端口：

```text
http://192.168.0.98:83/meta-ai/embed/chat
```

该入口不影响老服务，但浏览器会把 `http://局域网 IP:端口` 判定为非安全上下文，麦克风权限会被拒绝

如果局域网其他电脑需要使用语音输入，不要把现有 `83` 端口改成 HTTPS。应新增独立 HTTPS 端口，例如：

```text
https://192.168.0.98:8443/meta-ai/embed/chat
```

`83` 继续服务老系统和原有访问地址，`8443` 只作为需要麦克风权限的 MetaAI HTTPS 入口。完整 Nginx 自签证书示例见 `docker/compose/README.md`

当前 `docker-compose-host.yaml` 已为 Nginx 增加独立 HTTPS 端口和证书挂载：

```yaml
ports:
  - "8443:8443"
volumes:
  - ./data/gateway/nginx.conf:/etc/nginx/nginx.conf
  - ./data/gateway/certs:/etc/nginx/certs:ro
```

把证书放到服务器部署目录：

```bash
mkdir -p ./data/gateway/certs
cp meta-ai-ip.crt ./data/gateway/certs/
cp meta-ai-ip.key ./data/gateway/certs/
```

在现有 `./data/gateway/nginx.conf` 中保留原来的 `server { listen 80; ... }` 不变，在它后面、`http {}` 结束前追加：

```nginx
server {
    listen 8443 ssl;
    server_name 192.168.0.98;

    ssl_certificate /etc/nginx/certs/meta-ai-ip.crt;
    ssl_certificate_key /etc/nginx/certs/meta-ai-ip.key;

    client_max_body_size 3000m;

    # MetaAI 前端页面
    location /meta-ai/ {
        proxy_pass http://meta-ai-agent:8008/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }

    # MetaAI 普通聊天 SSE
    location /api/meta-ai/v1/chat {
        rewrite ^/api/meta-ai/(.*)$ /$1 break;
        proxy_pass http://meta-ai-agent:8008;
        proxy_http_version 1.1;
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }

    # MetaAI RAG 聊天 SSE
    location /api/meta-ai/v1/rag {
        rewrite ^/api/meta-ai/(.*)$ /$1 break;
        proxy_pass http://meta-ai-agent:8008;
        proxy_http_version 1.1;
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }

    # MetaAI 其他 API
    location /api/meta-ai/ {
        rewrite ^/api/meta-ai/(.*)$ /$1 break;
        proxy_pass http://meta-ai-agent:8008;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }

    # MetaAI ASR WebSocket
    location /asr/ws {
        proxy_pass http://live-asr:10095/;
        proxy_http_version 1.1;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }

    location / {
        return 404;
    }
}
```

检查并重启 Nginx：

```bash
docker compose exec nginx nginx -t
docker compose up -d --no-deps --force-recreate nginx
```

开发临时调试可在当前 Chrome 浏览器中打开：

```text
chrome://flags/#unsafely-treat-insecure-origin-as-secure
```

加入：

```text
http://192.168.0.98:83
```

该方案只影响当前浏览器，不影响服务器和其他用户，不作为生产方案

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

RustFS 报 `Permission denied (os error 13)` 时，优先修复宿主机挂载目录权限：

```bash
docker compose stop rustfs
sudo mkdir -p .data/rustfs
sudo chown -R 10001:10001 .data/rustfs
sudo chmod -R u+rwX,g+rwX .data/rustfs
docker compose up -d rustfs
docker compose logs -f rustfs
```

停机但保留数据：

```bash
docker compose down
```

## 注意事项

切换 Embedding 模型后必须重建向量库索引并重新入库，否则向量维度或语义空间不一致，会直接破坏检索质量

宿主机 Ollama、容器 Redis Stack、Qdrant、RustFS 的数据分别独立持久化，不要把 Ollama 模型目录放进本 Compose 的 `.data`

RustFS 容器进程不是 root，`.data/rustfs` 不要用 `chmod -R 777` 粗暴处理，也不要为了绕过权限把 RustFS 改成 root 运行

生产环境应替换默认 token、Redis 密码、RustFS 密钥和数据库密码
