# PaddleX OCR 官方 GPU AMD64 镜像

本目录用于构建 MetaAI PaddleX OCR 官方 GPU 镜像，仅支持 `linux/amd64`

基础镜像来自 PaddleX 官方文档：

```text
ccr-2vdh3abv-pub.cnc.bj.baidubce.com/paddlex/paddlex:paddlex3.3.11-paddlepaddle3.2.0-gpu-cuda11.8-cudnn8.9-trt8.6
```

官方出处：https://paddlepaddle.github.io/PaddleX/3.3/en/installation/installation.html

## 前置要求

构建机、推送机和运行机要求不同，不要混在一起判断

构建机要求：

- 不要求有 NVIDIA GPU
- 必须能运行 Docker Desktop 或 Docker Engine
- 必须能使用 Buildx，按 `docker/README.md` 初始化 `metax-multiarch`
- 必须能访问 PaddleX 官方基础镜像源和阿里云镜像仓库

推送要求：

- 必须已登录阿里云镜像仓库：

```powershell
docker login --username=331860137@qq.com registry.cn-hangzhou.aliyuncs.com
```

运行机要求：

- 必须是 `linux/amd64` 运行环境，Windows 上必须使用 Docker Desktop Linux containers
- 必须有 NVIDIA GPU
- 必须安装宿主机 NVIDIA Driver
- 必须安装 NVIDIA Container Toolkit
- 宿主机 NVIDIA Driver 必须兼容镜像内 CUDA 11.8

镜像内已经包含 CUDA 11.8、cuDNN 8.9、TensorRT 8.6、PaddlePaddle 和 PaddleX 用户态依赖，但不包含宿主机 NVIDIA Driver。
容器运行时通过 `--gpus all` 把宿主机 GPU 设备和驱动能力挂进容器

运行机先验证宿主机 GPU：

```bash
nvidia-smi
```

再验证 Docker GPU runtime：

```bash
docker run --rm --gpus all nvidia/cuda:11.8.0-base-ubuntu22.04 nvidia-smi
```

离线运行机也必须提前安装好 Docker、NVIDIA Driver 和 NVIDIA Container Toolkit。离线包只解决镜像分发，不解决宿主机 GPU 驱动和
Docker GPU runtime 安装

不能使用该镜像的场景：

- 普通 CPU 机器不能运行该 GPU 镜像
- ARM / Jetson 不能使用该 amd64 GPU 镜像
- 只安装 NVIDIA Driver 但没有安装 NVIDIA Container Toolkit 时，`docker run --gpus all ...` 仍然不可用

## 架构检查

```bash
docker buildx imagetools inspect ccr-2vdh3abv-pub.cnc.bj.baidubce.com/paddlex/paddlex:paddlex3.3.11-paddlepaddle3.2.0-gpu-cuda11.8-cudnn8.9-trt8.6
```

该官方 tag 当前不是 multi-arch index，不要把它发布成 arm64 GPU 镜像

## PyPI 源

Dockerfile 默认使用阿里云 PyPI 源，避免基础镜像内置源或部分镜像源不稳定导致构建失败

当前不建议使用清华 PyPI 源，本地构建时已经出现 `filetype-1.2.0` 下载 403

## 本地构建并手动推送

构建前先按 `docker/README.md` 初始化并启用 `metax-multiarch` Buildx 构建器

GPU 镜像使用 multi-stage 构建。`model-cache` 阶段基于官方 CPU 镜像预热 OCR 模型缓存，final 阶段再复制到官方 GPU 镜像

这样构建阶段不会在 GPU 镜像内执行 `create_pipeline("OCR")`，避免 buildx 环境缺少 `libcuda.so.1` 导致失败

单平台镜像使用 `--load` 先导入本地 Docker，再手动 `docker push`。不要在本场景中直接使用 `--push`，否则大镜像上传阶段不方便确认本地构建结果和重试推送

Windows PowerShell：

```powershell
docker buildx build `
  --platform linux/amd64 `
  -t registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-official-gpu-amd64 `
  .\docker\official-gpu `
  --load
docker images registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr
docker push registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-official-gpu-amd64
docker buildx imagetools inspect registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-official-gpu-amd64
```

Linux / macOS / Git Bash：

```bash
docker buildx build \
  --platform linux/amd64 \
  -t registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-official-gpu-amd64 \
  docker/official-gpu \
  --load
docker images registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr
docker push registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-official-gpu-amd64
docker buildx imagetools inspect registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-official-gpu-amd64
```

## 启动

```bash
docker run -d --name paddlex-ocr-gpu \
  --gpus all \
  -p 8080:8080 \
  registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-official-gpu-amd64
```

Windows PowerShell：

```powershell
docker run -d --name paddlex-ocr-gpu --gpus all -p 8080:8080 registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-official-gpu-amd64
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
docker save registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-official-gpu-amd64 -o official-gpu.tar
```

离线机器导入：

```powershell
docker load -i official-gpu.tar
```
