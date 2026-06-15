# MetaAI FunASR 实时语音 Docker 镜像

本目录维护 FunASR 实时语音转文字 CPU 服务镜像

前端语音输入优先接入 FunASR Runtime WebSocket service，不使用 OpenAI 兼容 `/v1/audio/transcriptions` 作为实时输入首选协议。OpenAI
兼容接口更适合录音文件转写，不适合浏览器麦克风边说边转文字

## 目录说明

| 目录                    | 来源            | 平台                            | 设备  | 用途     |
|-----------------------|---------------|-------------------------------|-----|--------|
| `official-online-cpu` | FunASR 官方实时镜像 | `linux/amd64` / `linux/arm64` | CPU | 生产优先路径 |

## 基础镜像

当前生产基础镜像固定为：

```text
registry.cn-hangzhou.aliyuncs.com/funasr_repo/funasr:funasr-runtime-sdk-online-cpu-0.1.13
```

不要使用 `latest`。阿里云 Registry 当前公开 tag 列表没有 `latest`，而且 tag 返回顺序不代表版本顺序。判断最新版本必须按语义版本排序，
`0.1.13` 大于 `0.1.9`

查询最新实时 CPU tag：

```powershell
$authUrl = 'https://dockerauth.cn-hangzhou.aliyuncs.com/auth?service=registry.aliyuncs.com%3Acn-hangzhou%3A26842&scope=repository%3Afunasr_repo%2Ffunasr%3Apull'
$token = (Invoke-RestMethod -Uri $authUrl -Method Get).token
$tags = Invoke-RestMethod `
  -Uri 'https://registry.cn-hangzhou.aliyuncs.com/v2/funasr_repo/funasr/tags/list' `
  -Headers @{ Authorization = "Bearer $token" } `
  -Method Get
$tags.tags |
  Where-Object { $_ -match '^funasr-runtime-sdk-online-cpu-(\d+\.\d+\.\d+)$' } |
  Sort-Object { [version]($_ -replace '^funasr-runtime-sdk-online-cpu-', '') } |
  Select-Object -Last 1
```

## 镜像 Tag

```text
registry.cn-hangzhou.aliyuncs.com/metax/live-asr:v1.0.0
```

CPU 正式 tag 是双平台 manifest list，包含 `linux/amd64` 和 `linux/arm64`

对外 tag 使用 `v1.0.0`，不要把底层基础镜像版本直接暴露到发布镜像名中。底层 FunASR 版本只在基础镜像说明和构建记录中保留

## Buildx 构建器

推荐直接使用 Docker Desktop 当前 context 对应的默认构建器，不再额外创建 `metax-multiarch` 这类独立 builder

默认构建器可以复用 Docker Desktop 已有的镜像层和 BuildKit 缓存，通常比新建 `docker-container` 类型 builder 更快。新建
builder 会拥有独立缓存空间，首次双平台构建时容易重新下载基础镜像层和模型依赖

先查看当前 Docker context 和 Buildx builder：

```powershell
docker context show
docker buildx ls
```

当前 context 为 `desktop-linux` 时，使用 Docker Desktop 的默认 builder：

```powershell
docker buildx use desktop-linux
```

当前 context 为 `default` 时，先切换 Docker context，再使用 default builder：

```powershell
docker context use default
docker buildx use default
```

`desktop-linux` 和 `default` 是两个不同 Docker context 下的默认构建器，二选一即可，不要连续执行两组命令。当前正在使用哪个
Docker context，就选择对应的 builder

## 服务协议

FunASR WebSocket 默认容器端口为 `10095`，本仓库文档统一映射到宿主机 `10096`

前端首帧 JSON：

```json
{
  "mode": "2pass",
  "wav_name": "meta-ai-console",
  "is_speaking": true,
  "wav_format": "pcm",
  "chunk_size": [
    5,
    10,
    5
  ],
  "chunk_interval": 10,
  "audio_fs": 16000,
  "itn": true
}
```

后续发送 16 kHz、16 bit、单声道 PCM 二进制分片。停止录音时发送：

```json
{
  "is_speaking": false
}
```

## 统一配置

前端开发环境建议配置：

```env
VITE_FUNASR_WS_URL=ws://localhost:10096
```

生产环境建议由 Nginx 或网关暴露同源 WebSocket 地址，例如：

```env
VITE_FUNASR_WS_URL=wss://meta-ai.example.com/funasr/ws
```

## 离线部署

离线部署必须在有网构建机上完成镜像构建和模型预下载。Dockerfile 会在构建期通过 ModelScope 下载模型到 `/workspace/models`
并打入镜像

离线机器按实际架构离线包执行。下面以 amd64 包为例：

```bash
docker load -i live-asr-v1.0.0-amd64.tar
docker run -d --name live-asr -p 10096:10095 registry.cn-hangzhou.aliyuncs.com/metax/live-asr:v1.0.0
```

离线启动时不要挂载空的 `/workspace/models`，否则会覆盖镜像内已经预置的模型缓存，导致运行期重新下载模型并失败

按目标运行机架构导出离线包。普通 `docker save` 导出的是本地已经拉取或构建的目标架构变体，不要假设一个 tar 一定同时包含
amd64 和 arm64

amd64 离线包导出示例：

```bash
docker pull --platform linux/amd64 registry.cn-hangzhou.aliyuncs.com/metax/live-asr:v1.0.0
docker save registry.cn-hangzhou.aliyuncs.com/metax/live-asr:v1.0.0 -o live-asr-v1.0.0-amd64.tar
```

arm64 离线包导出示例：

```bash
docker pull --platform linux/arm64 registry.cn-hangzhou.aliyuncs.com/metax/live-asr:v1.0.0
docker save registry.cn-hangzhou.aliyuncs.com/metax/live-asr:v1.0.0 -o live-asr-v1.0.0-arm64.tar
```

导入示例按实际文件名执行：

```bash
docker load -i live-asr-v1.0.0-amd64.tar
```

## 验收要求

所有进入生产 tag 的镜像都必须验证：

- 容器启动成功
- 宿主机 `10096` 能建立 WebSocket 连接
- `2pass-online` 能返回实时片段
- `2pass-offline` 能返回句尾修正文本
- 断网后容器仍能启动并识别，不访问 ModelScope 下载模型
- 前端麦克风开始、停止、重连和拒权提示正常
