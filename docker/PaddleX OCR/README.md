# MetaAI OCR Docker 镜像

本目录按镜像来源、架构和硬件能力拆分 PaddleX OCR 服务镜像

Java 侧统一通过 `PaddleOcrClient` 调用 `/ocr`，不感知底层是官方 CPU、官方 GPU 还是 openEuler CPU 镜像

## 目录说明

| 目录                      | 来源                          | 平台                            | 设备         | 用途                   |
|-------------------------|-----------------------------|-------------------------------|------------|----------------------|
| `official-cpu`          | PaddleX 官方镜像                | `linux/amd64`                 | CPU        | x86 CPU 生产优先路径       |
| `official-gpu`          | PaddleX 官方镜像                | `linux/amd64`                 | NVIDIA GPU | x86 GPU 加速路径         |
| `openeuler-cpu`         | openEuler PaddlePaddle 基础镜像 | `linux/amd64` / `linux/arm64` | CPU        | openEuler 双平台 CPU 路径 |
| `unofficial-arm64-cpu`  | openEuler PaddlePaddle 基础镜像 | `linux/arm64`                 | CPU        | 历史 ARM CPU 路径        |
| `unofficial-jetson-gpu` | 未固定                         | `linux/arm64`                 | Jetson GPU | 仅实验说明                |

## 镜像 Tag

```text
registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-official-cpu-amd64
registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-official-gpu-amd64
registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-openeuler-cpu-amd64
registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-openeuler-cpu-arm64
registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-openeuler-cpu
```

不要发布含糊的 `3.3.11-cpu` 或 `3.3.11-gpu` 作为正式镜像，来源、架构和硬件能力必须写进 tag

## 选择建议

- amd64 CPU：优先使用 `official-cpu`
- amd64 GPU：优先使用 `official-gpu`
- amd64 / arm64 双平台 CPU：使用 `openeuler-cpu`
- arm64 Jetson GPU：先按实验说明验证，不进入生产 tag

## PyPI 源

所有 Dockerfile 默认使用阿里云 PyPI 源：

```text
https://mirrors.aliyun.com/pypi/simple
```

当前不建议使用清华 PyPI 源，本地构建时已经出现 `filetype-1.2.0` 下载 403

如果需要临时改回官方 PyPI，可以在构建时显式覆盖：

```bash
--build-arg PIP_INDEX_URL=https://pypi.org/simple --build-arg PIP_TRUSTED_HOST=
```

## Buildx 构建器

统一使用名为 `metax-multiarch` 的 Buildx 构建器执行 PaddleX OCR 镜像构建：

```powershell
docker buildx create --name metax-multiarch --use
docker buildx use --default metax-multiarch
docker buildx inspect --bootstrap
```

第一条命令会创建一个名为 `metax-multiarch` 的 builder，并把当前 Docker CLI 切换到这个 builder。后续执行
`docker buildx build` 时，如果没有显式指定 `--builder`，默认会使用它

第二条命令会检查 builder 状态并启动 BuildKit。首次执行通常会拉取 `moby/buildkit:buildx-stable-1`，并创建
`buildx_buildkit_metax-multiarch0` 容器。这个容器不是 OCR 服务容器，只负责构建镜像

如果本机已经存在同名 builder，重新创建前先删除旧的：

```powershell
docker buildx rm -f metax-multiarch
docker buildx create --name metax-multiarch --use
docker buildx inspect --bootstrap
```

查看当前 builder：

```powershell
docker buildx ls
```

这个 builder 可以复用构建缓存，适合连续构建 `amd64 CPU`、`amd64 GPU` 和 `arm64 CPU` 镜像。缓存能减少重复拉取基础镜像、
重复安装依赖和重复下载 PaddleX OCR 模型的时间

如果磁盘空间紧张，可以删除构建缓存：

```powershell
docker builder prune -a -f
```

如果近期不再构建镜像，可以删除 builder：

```powershell
docker buildx rm -f metax-multiarch
```

注意：清理镜像、容器或 builder 后，Docker Desktop 的 `docker_data.vhdx` 文件大小不一定立刻下降。VHDX 是虚拟磁盘文件，
内部空间释放后仍可能需要 `wsl --shutdown` 和 VHDX 压缩才能归还给 Windows 文件系统

## 构建和推送策略

本目录中的正式镜像都是单平台镜像，统一使用 `docker buildx build --load` 先导入本地 Docker，再手动执行 `docker push`

不要在单平台大镜像构建命令中直接使用 `--push`。GPU 镜像体积较大，直接 `--push` 时上传阶段不方便确认本地构建结果和重试推送

典型流程：

```powershell
docker buildx build --platform linux/amd64 -t <image> <docker-dir> --load
docker images <image-repository>
docker push <image>
docker buildx imagetools inspect <image>
```

注意：`--load` 不适合一次输出多个平台的 manifest list。如果后续需要真正的 `linux/amd64,linux/arm64` 多平台合并 tag，仍然需要使用
`--push`

## 统一配置

应用配置保持：

```properties
metax.ai.rag.ocr.enabled=true
metax.ai.rag.ocr.provider=paddle
metax.ai.rag.ocr.base-url=http://localhost:8080
metax.ai.rag.ocr.endpoint=/ocr
```

## 离线部署

离线部署必须在有网构建机上完成镜像构建和 OCR 模型预热。Dockerfile 会在构建期初始化 PaddleX `OCR` pipeline，并把
`/root/.paddlex` 模型缓存打入镜像

离线机器只执行：

```bash
docker load -i <image>.tar
docker run -d --name paddlex-ocr -p 8080:8080 <image>
```

离线启动时不要挂载空的 `/root/.paddlex`，否则会覆盖镜像内已经预置的模型缓存，导致运行期重新下载模型并失败

导出示例：

```bash
docker save registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-official-cpu-amd64 -o official-cpu.tar
```

导入示例：

```bash
docker load -i official-cpu.tar
```

## 验收要求

所有镜像都必须验证：

- 容器启动成功
- `/health` 可访问
- `/docs` 可访问
- 真实扫描版 PDF 能完成 OCR
- `/ocr` 响应结构兼容 `result.ocrResults[*].prunedResult`
