# PaddleX OCR 官方 GPU AMD64 镜像

本目录用于构建 MetaAI PaddleX OCR 官方 GPU 镜像，仅支持 `linux/amd64`

基础镜像来自 PaddleX 官方文档：

```text
ccr-2vdh3abv-pub.cnc.bj.baidubce.com/paddlex/paddlex:paddlex3.3.11-paddlepaddle3.2.0-gpu-cuda11.8-cudnn8.9-trt8.6
```

官方出处：https://paddlepaddle.github.io/PaddleX/3.3/en/installation/installation.html

## 前置要求

- 构建机不要求有 GPU
- 运行机必须安装 NVIDIA Driver
- 运行机必须安装 NVIDIA Container Toolkit
- 运行机上 `docker run --gpus all ... nvidia-smi` 必须可用

## 架构检查

```bash
docker buildx imagetools inspect ccr-2vdh3abv-pub.cnc.bj.baidubce.com/paddlex/paddlex:paddlex3.3.11-paddlepaddle3.2.0-gpu-cuda11.8-cudnn8.9-trt8.6
```

该官方 tag 当前不是 multi-arch index，不要把它发布成 arm64 GPU 镜像

## PyPI 源

Dockerfile 默认使用阿里云 PyPI 源，避免基础镜像内置源或部分镜像源不稳定导致构建失败

当前不建议使用清华 PyPI 源，本地构建时已经出现 `filetype-1.2.0` 下载 403

## 构建并推送

构建前先按 `docker/README.md` 初始化并启用 `metax-multiarch` Buildx 构建器

GPU 镜像使用 multi-stage 构建。`model-cache` 阶段基于官方 CPU 镜像预热 OCR 模型缓存，final 阶段再复制到官方 GPU 镜像

这样构建阶段不会在 GPU 镜像内执行 `create_pipeline("OCR")`，避免 buildx 环境缺少 `libcuda.so.1` 导致失败

Windows PowerShell：

```powershell
docker buildx build `
  --platform linux/amd64 `
  -t registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-official-gpu-cuda11.8-cudnn8.9-trt8.6-amd64 `
  .\docker\paddlex-ocr-official-gpu-amd64 `
  --push
```

Linux / macOS / Git Bash：

```bash
docker buildx build \
  --platform linux/amd64 \
  -t registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-official-gpu-cuda11.8-cudnn8.9-trt8.6-amd64 \
  docker/paddlex-ocr-official-gpu-amd64 \
  --push
```

## 启动

```bash
docker run -d --name paddlex-ocr-gpu \
  --gpus all \
  -p 8080:8080 \
  registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-official-gpu-cuda11.8-cudnn8.9-trt8.6-amd64
```

Windows PowerShell：

```powershell
docker run -d --name paddlex-ocr-gpu --gpus all -p 8080:8080 registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-official-gpu-cuda11.8-cudnn8.9-trt8.6-amd64
```

离线部署时不要挂载空的 `/root/.paddlex`，否则会覆盖镜像内已经预置的 OCR 模型缓存

## 验证

查看容器是否能访问 GPU：

```bash
docker exec paddlex-ocr-gpu nvidia-smi
```

查看服务日志：

```bash
docker logs -f paddlex-ocr-gpu
```

服务启动成功后应能访问：

```text
http://localhost:8080/health
http://localhost:8080/docs
```

验收时必须使用同一份扫描版 PDF 对比 CPU 版，确认页数、文本和耗时

构建日志中不应再出现：

```text
ImportError: libcuda.so.1
```

## 离线包

有网构建机导出：

```powershell
docker save registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-official-gpu-cuda11.8-cudnn8.9-trt8.6-amd64 -o paddlex-ocr-official-gpu-amd64.tar
```

离线机器导入：

```powershell
docker load -i paddlex-ocr-official-gpu-amd64.tar
```
