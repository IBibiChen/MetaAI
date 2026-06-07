FROM openeuler/paddlepaddle:3.2.0-oe2403sp2

ARG TARGETPLATFORM
ARG PIP_INDEX_URL=https://pypi.org/simple
ARG PIP_TRUSTED_HOST=
ARG PADDLEX_VERSION=3.3.11

ENV PIP_INDEX_URL=${PIP_INDEX_URL}
ENV PIP_TRUSTED_HOST=${PIP_TRUSTED_HOST}
ENV PIP_DISABLE_PIP_VERSION_CHECK=1
ENV DISABLE_MODEL_SOURCE_CHECK=True

LABEL org.opencontainers.image.title="MetaAI PaddleX OCR ARM64 Experiment"
LABEL org.opencontainers.image.description="Experimental PaddleX OCR Basic Serving image based on openEuler PaddlePaddle multi-arch image"

# 该镜像用于验证 arm64 原生 PaddlePaddle 能否运行 PaddleX OCR Basic Serving
# openeuler/paddlepaddle:3.2.0-oe2403sp2 已通过 manifest 确认包含 linux/amd64 和 linux/arm64
RUN echo "Building MetaAI PaddleX OCR ARM64 experiment for TARGETPLATFORM=${TARGETPLATFORM}"

# 从 PaddlePaddle 多架构基础镜像安装 PaddleX 和 Basic Serving 依赖
# 这是 ARM 原生实验路径，不替代已跑通的 PaddleX 官方 amd64 镜像
RUN python -m pip install "paddlex==${PADDLEX_VERSION}" \
    && paddlex --install serving

EXPOSE 8080

# 保持和 Java 侧 PaddleOcrClient 一致的 /ocr JSON 协议
# 只使用 CPU 设备，避免 ARM 机器上引入 GPU / NPU 驱动差异
CMD ["paddlex", "--serve", "--pipeline", "OCR", "--host", "0.0.0.0", "--port", "8080", "--device", "cpu"]
