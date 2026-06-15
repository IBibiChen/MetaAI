# PaddleX OCR 非官方 CPU ARM64 镜像

本目录用于构建 MetaAI PaddleX OCR ARM64 CPU 镜像，仅支持 `linux/arm64`

该方案不是 PaddleX 官方预构建镜像，而是基于：

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

如果输出中没有 `linux/arm64`，不要继续构建 ARM 原生镜像

## PyPI 源

Dockerfile 默认使用阿里云 PyPI 源，避免基础镜像内置源或部分镜像源不稳定导致构建失败

当前不建议使用清华 PyPI 源，本地构建时已经出现 `filetype-1.2.0` 下载 403

## 本地构建并手动推送

构建前先按 `docker/PaddleX-OCR/README.md` 初始化并启用 `metax-multiarch` Buildx 构建器

单平台镜像使用 `--load` 先导入本地 Docker，再手动 `docker push`。不要在本场景中直接使用 `--push`，否则大镜像上传阶段不方便确认本地构建结果和重试推送

Windows PowerShell：

```powershell
docker buildx build `
  --platform linux/arm64 `
  -t registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-unofficial-cpu-arm64 `
  .\docker\PaddleX-OCR\unofficial-arm64-cpu `
  --load
docker images registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr
docker push registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-unofficial-cpu-arm64
docker buildx imagetools inspect registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-unofficial-cpu-arm64
```

Linux / macOS / Git Bash：

```bash
docker buildx build \
  --platform linux/arm64 \
  -t registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-unofficial-cpu-arm64 \
  docker/PaddleX-OCR/unofficial-arm64-cpu \
  --load
docker images registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr
docker push registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-unofficial-cpu-arm64
docker buildx imagetools inspect registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-unofficial-cpu-arm64
```

## 启动

```bash
docker run -d --name paddlex-ocr-arm64 \
  -p 8080:8080 \
  registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-unofficial-cpu-arm64
```

Windows PowerShell：

```powershell
docker run -d --name paddlex-ocr-arm64 -p 8080:8080 registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-unofficial-cpu-arm64
```

离线部署时不要挂载空的 `/root/.paddlex`，否则会覆盖镜像内已经预置的 OCR 模型缓存

## 验证

```bash
docker logs -f paddlex-ocr-arm64
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
docker save registry.cn-hangzhou.aliyuncs.com/metax/paddlex-ocr:3.3.11-unofficial-cpu-arm64 -o unofficial-arm64-cpu.tar
```

离线机器导入：

```powershell
docker load -i unofficial-arm64-cpu.tar
```

## 风险说明

该镜像是非官方 ARM CPU 路线，需要单独验收性能、内存和 OCR 结果稳定性

如果 ARM 原生 PaddleX 不稳定，不要继续把业务代码绑死在 PaddleX 上，应保留 `/ocr` HTTP 协议并替换底层 OCR 服务实现
