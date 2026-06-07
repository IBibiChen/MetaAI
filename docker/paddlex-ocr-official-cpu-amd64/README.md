# PaddleX OCR 官方 CPU AMD64 镜像

本目录用于构建 MetaAI PaddleX OCR 官方 CPU 镜像，仅支持 `linux/amd64`

基础镜像来自 PaddleX 官方文档：

```text
ccr-2vdh3abv-pub.cnc.bj.baidubce.com/paddlex/paddlex:paddlex3.3.11-paddlepaddle3.2.0-cpu
```

官方出处：https://paddlepaddle.github.io/PaddleX/3.3/en/installation/installation.html

## 架构检查

```bash
docker buildx imagetools inspect ccr-2vdh3abv-pub.cnc.bj.baidubce.com/paddlex/paddlex:paddlex3.3.11-paddlepaddle3.2.0-cpu
```

该官方 tag 当前不是 multi-arch index，不要把它发布成 arm64 镜像

## PyPI 源

Dockerfile 默认使用阿里云 PyPI 源，避免基础镜像内置源或部分镜像源不稳定导致构建失败

当前不建议使用清华 PyPI 源，本地构建时已经出现 `filetype-1.2.0` 下载 403

## 构建并推送

构建前先按 `docker/README.md` 初始化并启用 `metax-multiarch` Buildx 构建器

Windows PowerShell：

```powershell
docker buildx build `
  --platform linux/amd64 `
  -t registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-official-cpu-amd64 `
  .\docker\paddlex-ocr-official-cpu-amd64 `
  --push
```

Linux / macOS / Git Bash：

```bash
docker buildx build \
  --platform linux/amd64 \
  -t registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-official-cpu-amd64 \
  docker/paddlex-ocr-official-cpu-amd64 \
  --push
```

## 启动

```bash
docker run -d --name paddlex-ocr \
  -p 8080:8080 \
  registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-official-cpu-amd64
```

Windows PowerShell：

```powershell
docker run -d --name paddlex-ocr -p 8080:8080 registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-official-cpu-amd64
```

离线部署时不要挂载空的 `/root/.paddlex`，否则会覆盖镜像内已经预置的 OCR 模型缓存

## 验证

```bash
docker logs -f paddlex-ocr
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

## 离线包

有网构建机导出：

```powershell
docker save registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-official-cpu-amd64 -o paddlex-ocr-official-cpu-amd64.tar
```

离线机器导入：

```powershell
docker load -i paddlex-ocr-official-cpu-amd64.tar
```
