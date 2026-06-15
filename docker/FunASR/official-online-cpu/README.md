# FunASR 官方实时 CPU 镜像

本目录用于构建 MetaAI FunASR 实时语音 CPU 镜像，基础镜像固定为：

```text
registry.cn-hangzhou.aliyuncs.com/funasr_repo/funasr:funasr-runtime-sdk-online-cpu-0.1.13
```

该镜像用于浏览器麦克风实时语音转文字，服务协议为 FunASR Runtime WebSocket `2pass`

## 架构检查

发布双平台 CPU 镜像前必须先确认官方基础镜像支持目标平台：

```bash
docker buildx imagetools inspect registry.cn-hangzhou.aliyuncs.com/funasr_repo/funasr:funasr-runtime-sdk-online-cpu-0.1.13
```

如果输出中没有 `linux/arm64`，不要发布双平台 `v1.0.0`

2026-06-15 已验证该官方 tag 包含：

```text
linux/amd64
linux/arm64
```

## 本地单平台构建

构建前先按 `docker/FunASR/README.md` 初始化并启用 `metax-multiarch` Buildx 构建器

本地开发快速验证使用 `--load`，只构建当前要运行的单个平台。不要把 `--load` 和 `--platform linux/amd64,linux/arm64`
混用，Docker 本地镜像存储不适合承接这个双平台 manifest 输出

本地 `--load` 使用同一个业务 tag `v1.0.0`，但它只代表当前机器导入的单平台镜像。正式发布前仍必须执行后面的双平台 `--push`

Windows PowerShell：

```powershell
docker buildx build `
  --platform linux/amd64 `
  -t registry.cn-hangzhou.aliyuncs.com/metax/live-asr:v1.0.0 `
  ".\docker\FunASR\official-online-cpu" `
  --load
docker images registry.cn-hangzhou.aliyuncs.com/metax/live-asr
```

Linux / macOS / Git Bash：

```bash
docker buildx build \
  --platform linux/amd64 \
  -t registry.cn-hangzhou.aliyuncs.com/metax/live-asr:v1.0.0 \
  docker/FunASR/official-online-cpu \
  --load
docker images registry.cn-hangzhou.aliyuncs.com/metax/live-asr
```

ARM64 本地调试机使用：

```powershell
docker buildx build `
  --platform linux/arm64 `
  -t registry.cn-hangzhou.aliyuncs.com/metax/live-asr:v1.0.0 `
  ".\docker\FunASR\official-online-cpu" `
  --load
```

## 正式双平台发布

正式发布使用 `--push` 直接推送双平台 manifest list：

该命令会分别构建 `linux/amd64` 和 `linux/arm64` 变体，并把同一个业务 tag 发布成 manifest list。运行机执行 `docker pull`
时会按自身架构自动选择对应变体

Windows PowerShell：

```powershell
docker buildx build `
  --platform linux/amd64,linux/arm64 `
  -t registry.cn-hangzhou.aliyuncs.com/metax/live-asr:v1.0.0 `
  ".\docker\FunASR\official-online-cpu" `
  --push
docker buildx imagetools inspect registry.cn-hangzhou.aliyuncs.com/metax/live-asr:v1.0.0
```

Linux / macOS / Git Bash：

```bash
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t registry.cn-hangzhou.aliyuncs.com/metax/live-asr:v1.0.0 \
  docker/FunASR/official-online-cpu \
  --push
docker buildx imagetools inspect registry.cn-hangzhou.aliyuncs.com/metax/live-asr:v1.0.0
```

验收 manifest 输出必须同时包含：

```text
Platform: linux/amd64
Platform: linux/arm64
```

## 启动

```bash
docker run -d --name live-asr \
  -p 10096:10095 \
  registry.cn-hangzhou.aliyuncs.com/metax/live-asr:v1.0.0
```

Windows PowerShell：

```powershell
docker run -d --name live-asr -p 10096:10095 registry.cn-hangzhou.aliyuncs.com/metax/live-asr:v1.0.0
```

离线部署时不要挂载空的 `/workspace/models`，否则会覆盖镜像内已经预置的模型缓存

## 可选运行参数

```bash
docker run -d --name live-asr \
  -p 10096:10095 \
  -e FUNASR_DECODER_THREAD_NUM=4 \
  -e FUNASR_IO_THREAD_NUM=2 \
  -e FUNASR_MODEL_THREAD_NUM=1 \
  registry.cn-hangzhou.aliyuncs.com/metax/live-asr:v1.0.0
```

可覆盖的环境变量：

```text
FUNASR_PORT
FUNASR_MODEL_ROOT
FUNASR_MODEL_DIR
FUNASR_ONLINE_MODEL_DIR
FUNASR_VAD_DIR
FUNASR_PUNC_DIR
FUNASR_LM_DIR
FUNASR_ITN_DIR
FUNASR_HOTWORD
FUNASR_CERTFILE
FUNASR_KEYFILE
FUNASR_DECODER_THREAD_NUM
FUNASR_IO_THREAD_NUM
FUNASR_MODEL_THREAD_NUM
```

## 验证

查看日志：

```bash
docker logs -f live-asr
```

使用官方 Python client 验证：

```bash
python3 funasr_wss_client.py --host "127.0.0.1" --port 10096 --mode 2pass
```

前端开发环境配置：

```env
VITE_FUNASR_WS_URL=ws://localhost:10096
```

## 离线包

按目标运行机架构导出离线包。普通 `docker save` 导出的是本地已拉取或构建的目标架构变体，不要把 amd64 和 arm64 混成一个含糊
tar

离线包文件名必须保留架构后缀，镜像 tag 不保留架构后缀。这两个概念不要混用：tag 面向在线仓库的自动架构选择，tar
文件面向某台离线机器的实际架构

amd64 离线包：

```powershell
docker pull --platform linux/amd64 registry.cn-hangzhou.aliyuncs.com/metax/live-asr:v1.0.0
docker save registry.cn-hangzhou.aliyuncs.com/metax/live-asr:v1.0.0 -o live-asr-v1.0.0-amd64.tar
```

arm64 离线包：

```powershell
docker pull --platform linux/arm64 registry.cn-hangzhou.aliyuncs.com/metax/live-asr:v1.0.0
docker save registry.cn-hangzhou.aliyuncs.com/metax/live-asr:v1.0.0 -o live-asr-v1.0.0-arm64.tar
```

离线机器按实际文件名导入：

```powershell
docker load -i live-asr-v1.0.0-amd64.tar
```
