# PaddleX OCR openEuler CPU 双平台镜像

本目录用于构建 MetaAI PaddleX OCR openEuler CPU 镜像，支持 `linux/amd64` 和 `linux/arm64`

该方案基于 PaddlePaddle openEuler 多架构基础镜像：

```text
openeuler/paddlepaddle:3.2.0-oe2403sp2
```

重新安装：

```text
paddlex[ocr]==3.3.11
paddlex --install serving
```

## 架构检查

```bash
docker buildx imagetools inspect openeuler/paddlepaddle:3.2.0-oe2403sp2
```

预期能看到：

```text
Platform: linux/amd64
Platform: linux/arm64
```

如果输出中没有 `linux/amd64` 和 `linux/arm64`，不要继续构建双平台 CPU 镜像

## PyPI 源

Dockerfile 默认使用阿里云 PyPI 源，避免基础镜像内置源或部分镜像源不稳定导致构建失败

当前不建议使用清华 PyPI 源，本地构建时已经出现 `filetype-1.2.0` 下载 403

## AMD64 本地构建并手动推送

构建前先按 `docker/PaddleX-OCR/README.md` 初始化并启用 `metax-multiarch` Buildx 构建器

Windows PowerShell：

```powershell
docker buildx build `
  --platform linux/amd64 `
  -t registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-openeuler-cpu-amd64 `
  .\docker\PaddleX-OCR\openeuler-cpu `
  --load
docker images registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr
docker push registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-openeuler-cpu-amd64
docker buildx imagetools inspect registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-openeuler-cpu-amd64
```

Linux / macOS / Git Bash：

```bash
docker buildx build \
  --platform linux/amd64 \
  -t registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-openeuler-cpu-amd64 \
  docker/PaddleX-OCR/openeuler-cpu \
  --load
docker images registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr
docker push registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-openeuler-cpu-amd64
docker buildx imagetools inspect registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-openeuler-cpu-amd64
```

## ARM64 构建并推送

在 amd64 Windows / Linux 构建机上交叉构建 ARM64 镜像时，推荐直接 `--push`。不要在 amd64 构建机上依赖 `--load`
导入 ARM64 镜像，本地 Docker Engine 对非本机平台镜像的加载和运行验证不稳定

Windows PowerShell：

```powershell
docker buildx build `
  --platform linux/arm64 `
  -t registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-openeuler-cpu-arm64 `
  .\docker\PaddleX-OCR\openeuler-cpu `
  --push
docker buildx imagetools inspect registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-openeuler-cpu-arm64
```

Linux / macOS / Git Bash：

```bash
docker buildx build \
  --platform linux/arm64 \
  -t registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-openeuler-cpu-arm64 \
  docker/PaddleX-OCR/openeuler-cpu \
  --push
docker buildx imagetools inspect registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-openeuler-cpu-arm64
```

## 多平台合并 Tag

如果需要同一个 tag 同时支持 `linux/amd64` 和 `linux/arm64`，使用 manifest list 输出，必须直接 `--push`

Windows PowerShell：

```powershell
docker buildx build `
  --platform linux/amd64,linux/arm64 `
  -t registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-openeuler-cpu `
  .\docker\PaddleX-OCR\openeuler-cpu `
  --push
docker buildx imagetools inspect registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-openeuler-cpu
```

```powershell
docker buildx build `
  --platform linux/amd64,linux/arm64 `
  -t registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:v1.0.0 `
  .\docker\PaddleX-OCR\openeuler-cpu `
  --push
docker buildx imagetools inspect registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:v1.0.0
```

## 启动

```bash
docker run -d --name openeuler-cpu \
  -p 8080:8080 \
  registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-openeuler-cpu-amd64
```

Windows PowerShell：

```powershell
docker run -d --name openeuler-cpu -p 8080:8080 registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-openeuler-cpu-amd64
```

```powershell
docker run -d --name openeuler-cpu -p 8080:8080 registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:v1.0.0
```

离线部署时不要挂载空的 `/root/.paddlex`，否则会覆盖镜像内已经预置的 OCR 模型缓存

## 验证

```bash
docker logs -f openeuler-cpu
```

服务启动成功后应能访问：

```text
http://localhost:8080/health
http://localhost:8080/docs
```

验收时必须上传真实扫描版 PDF，确认 Java 日志中出现：

```text
开始调用 PaddleOCR
PaddleOCR 调用完成：pages =
PDF OCR 文档读取完成
```

## 故障排查

`openeuler/paddlepaddle:3.2.0-oe2403sp2` 基础镜像中使用 `python3` 命令，不要假设存在 `python`

如果容器启动时报 `ImportError: libGL.so.1`，说明 OpenCV 运行时系统库缺失。openEuler 24.03 中 `libGL.so.1` 由
`libglvnd-glx` 提供，不要照搬 Debian / Ubuntu 的包名

如果服务启动时报 `DependencyError: OCR requires additional dependencies`，说明没有安装 `paddlex[ocr]`

## 离线包

有网构建机导出：

```powershell
docker save registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-openeuler-cpu-amd64 -o openeuler-cpu-amd64.tar
```

离线机器导入：

```powershell
docker load -i openeuler-cpu-amd64.tar
```
