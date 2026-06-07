# PaddleX OCR ARM64 原生实验镜像

本目录用于验证 ARM 平台是否可以原生运行 PaddleX OCR Basic Serving

该方案基于 `openeuler/paddlepaddle:3.2.0-oe2403sp2`，不是 PaddleX 官方预构建镜像。当前只作为实验路径，不能直接替代已经跑通的
`docker/paddlex-ocr` amd64 镜像

## 基础镜像架构检查

构建前必须确认基础镜像包含 `linux/arm64`：

```bash
docker buildx imagetools inspect openeuler/paddlepaddle:3.2.0-oe2403sp2
```

预期能看到：

```text
MediaType: application/vnd.oci.image.index.v1+json
Platform: linux/amd64
Platform: linux/arm64
```

如果输出中没有 `linux/arm64`，不要继续构建 ARM 原生镜像

## arm64 构建

ARM 机器本地构建：

```bash
docker buildx build --platform linux/arm64 -t metax/paddlex-ocr:3.3.11-cpu-arm64 docker/paddlex-ocr-arm64-experiment --load
```

Windows PowerShell 远程或 Docker Desktop 构建：

```powershell
docker buildx build --platform linux/arm64 -t metax/paddlex-ocr:3.3.11-cpu-arm64 .\docker\paddlex-ocr-arm64-experiment --load
```

如果 PyPI 官方源访问较慢，可以改用阿里云 PyPI 源：

```bash
docker buildx build --platform linux/arm64 --build-arg PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple --build-arg PIP_TRUSTED_HOST=mirrors.aliyun.com -t metax/paddlex-ocr:3.3.11-cpu-arm64 docker/paddlex-ocr-arm64-experiment --load
```

Windows PowerShell：

```powershell
docker buildx build --platform linux/arm64 --build-arg PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple --build-arg PIP_TRUSTED_HOST=mirrors.aliyun.com -t metax/paddlex-ocr:3.3.11-cpu-arm64 .\docker\paddlex-ocr-arm64-experiment --load
```

## 启动

```bash
docker run -d --name paddlex-ocr-arm64 -p 8080:8080 -v ${PWD}/docker-data/paddlex-arm64:/root/.paddlex metax/paddlex-ocr:3.3.11-cpu-arm64
```

Windows PowerShell：

```powershell
docker run -d --name paddlex-ocr-arm64 -p 8080:8080 -v ${PWD}\docker-data\paddlex-arm64:/root/.paddlex metax/paddlex-ocr:3.3.11-cpu-arm64
```

`/root/.paddlex` 是 PaddleX 模型缓存目录，首次启动会下载 OCR 模型

## 验证

查看日志：

```bash
docker logs -f paddlex-ocr-arm64
```

服务启动成功后应能访问：

```text
http://localhost:8080/health
http://localhost:8080/docs
```

Java 侧配置保持不变：

```properties
metax.ai.rag.ocr.enabled=true
metax.ai.rag.ocr.provider=paddle
metax.ai.rag.ocr.base-url=http://localhost:8080
metax.ai.rag.ocr.endpoint=/ocr
```

验收时必须上传真实扫描版 PDF，确认 Java 日志中出现：

```text
选择文档 Reader 策略：documentType = pdf
开始调用 PaddleOCR
PaddleOCR 调用完成：pages =
PDF OCR 文档读取完成
```

## 风险说明

当前镜像是 ARM 原生实验路径，风险点包括：

- PaddleX 在 openEuler PaddlePaddle 基础镜像上的依赖完整性
- PaddleX Basic Serving 在 ARM 上的启动稳定性
- OCR 模型在 ARM CPU 上的性能和内存占用
- `/ocr` 实际响应结构是否保持 `result.ocrResults[*].prunedResult`

如果 ARM 原生 PaddleX 不稳定，不要继续把业务代码绑死在 PaddleX 上。正确方向是保留 Java 侧 `/ocr` HTTP 协议，再替换底层 OCR
服务实现，例如 RapidOCR / ONNX Runtime 或 Tesseract 适配服务

## 清理

```bash
docker rm -f paddlex-ocr-arm64
```
