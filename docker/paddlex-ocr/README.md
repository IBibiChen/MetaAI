# PaddleX OCR 镜像

本目录用于构建 MetaAI 本地扫描版 PDF OCR 服务镜像

镜像基于 PaddleX CPU 固定版本，启动 PaddleX Basic Serving 的 `OCR` pipeline，供 `PaddleOcrClient` 调用 `/ocr`

当前方案按 `linux/amd64` 设计。不要直接发布假的 multi-arch tag，PaddleX / PaddlePaddle 基础镜像是否支持 `linux/arm64` 必须以
manifest 为准

## 基础镜像架构检查

构建前先检查上游 PaddleX 基础镜像支持的平台：

```bash
docker buildx imagetools inspect ccr-2vdh3abv-pub.cnc.bj.baidubce.com/paddlex/paddlex:paddlex3.3.11-paddlepaddle3.2.0-cpu
```

如果输出中没有 `linux/arm64`，就不能构建真正的 arm64 镜像。`buildx` 只能编排构建，不能让不支持 arm64 的 PaddlePaddle
基础镜像变成原生 arm64

## amd64 构建

默认使用 PyPI 官方源，避免基础镜像内置源返回 403 造成构建失败

Linux / macOS / Git Bash：

```bash
docker buildx build --platform linux/amd64 -t metax/paddlex-ocr:3.3.11-cpu-amd64 docker/paddlex-ocr --load
```

Windows PowerShell：

```powershell
docker buildx build --platform linux/amd64 -t metax/paddlex-ocr:3.3.11-cpu-amd64 .\docker\paddlex-ocr --load
```

如果 PyPI 官方源访问较慢，可以改用阿里云 PyPI 源

Linux / macOS / Git Bash：

```bash
docker buildx build --platform linux/amd64 --build-arg PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple --build-arg PIP_TRUSTED_HOST=mirrors.aliyun.com -t metax/paddlex-ocr:3.3.11-cpu-amd64 docker/paddlex-ocr --load
```

Windows PowerShell：

```powershell
docker buildx build --platform linux/amd64 --build-arg PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple --build-arg PIP_TRUSTED_HOST=mirrors.aliyun.com -t metax/paddlex-ocr:3.3.11-cpu-amd64 .\docker\paddlex-ocr --load
```

当前不建议使用清华 PyPI 源，本地构建时已经出现 `filetype-1.2.0` 下载 403

## 启动

Linux / macOS / Git Bash：

```bash
docker run -d --name paddlex-ocr -p 8080:8080 -v ${PWD}/docker-data/paddlex:/root/.paddlex metax/paddlex-ocr:3.3.11-cpu-amd64
```

Windows PowerShell：

```powershell
docker run -d --name paddlex-ocr -p 8080:8080 -v ${PWD}\docker-data\paddlex:/root/.paddlex metax/paddlex-ocr:3.3.11-cpu-amd64
```

`/root/.paddlex` 是 PaddleX 模型缓存目录，挂载后可以避免容器重建时重复下载 OCR 模型

## ARM 机器临时验证

如果在 ARM 机器上只想临时验证，可以使用 amd64 仿真运行：

```bash
docker run --platform linux/amd64 -d --name paddlex-ocr -p 8080:8080 -v ${PWD}/docker-data/paddlex:/root/.paddlex metax/paddlex-ocr:3.3.11-cpu-amd64
```

这不是原生 arm64 镜像，性能和稳定性都不能按生产方案看待

## 验证

查看日志：

```bash
docker logs -f paddlex-ocr
```

应用配置需要保持：

```properties
metax.ai.rag.ocr.enabled=true
metax.ai.rag.ocr.provider=paddle
metax.ai.rag.ocr.base-url=http://localhost:8080
metax.ai.rag.ocr.endpoint=/ocr
```

## 清理

```bash
docker rm -f paddlex-ocr
```

## 说明

第一版固定 CPU amd64 镜像，目标是本地功能验证

GPU 版本不要直接改这个镜像，后续应单独新增 GPU Dockerfile，并明确 CUDA、PaddlePaddle 和 NVIDIA runtime 版本

真正全平台镜像需要上游 OCR 引擎提供对应架构支持。若后续必须支持 arm64，应单独评估 arm64 OCR 服务实现，而不是强行把当前
PaddleX 镜像打成 multi-arch
