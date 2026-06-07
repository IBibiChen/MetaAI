# PaddleX OCR 非官方 Jetson GPU 实验

本目录仅记录 Jetson GPU 方向的实验边界，当前不提供正式 Dockerfile

不要把 amd64 CUDA 镜像直接用于 Jetson。Jetson 使用 L4T / JetPack 生态，CUDA、cuDNN、TensorRT 和系统库版本都必须跟宿主机匹配

## 实验前置确认

必须先确认：

- Jetson 型号
- JetPack / L4T 版本
- CUDA 版本
- cuDNN 版本
- TensorRT 版本
- PaddlePaddle 是否存在可用的 aarch64 GPU wheel
- PaddleX OCR pipeline 是否能在该 PaddlePaddle 构建上运行

## 当前结论

PaddleX 官方 amd64 GPU 镜像不能作为 Jetson ARM64 GPU 镜像使用

当前生产可用路径是：

- amd64 GPU 使用 `paddlex-ocr-official-gpu-amd64`
- arm64 CPU 使用 `paddlex-ocr-unofficial-cpu-arm64`
- Jetson GPU 单独实验，未通过前不发布生产 tag

## Java 侧约束

Java 侧继续只依赖 `/ocr` HTTP 协议，不允许把 Jetson、CUDA、TensorRT 等硬件细节写进业务代码

如果 Jetson GPU 路线不可控，优先评估 RapidOCR / ONNX Runtime / TensorRT OCR 服务实现，并保持 `/ocr` 响应结构兼容
