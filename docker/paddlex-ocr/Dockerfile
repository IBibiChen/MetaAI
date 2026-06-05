FROM ccr-2vdh3abv-pub.cnc.bj.baidubce.com/paddlex/paddlex:paddlex3.3.11-paddlepaddle3.2.0-cpu

ARG TARGETPLATFORM
ARG PIP_INDEX_URL=https://pypi.org/simple
ARG PIP_TRUSTED_HOST=

ENV PIP_INDEX_URL=${PIP_INDEX_URL}
ENV PIP_TRUSTED_HOST=${PIP_TRUSTED_HOST}
ENV PIP_DISABLE_PIP_VERSION_CHECK=1
ENV DISABLE_MODEL_SOURCE_CHECK=True

LABEL org.opencontainers.image.title="MetaAI PaddleX OCR"
LABEL org.opencontainers.image.description="PaddleX OCR Basic Serving image for MetaAI scanned PDF ingestion"

# 当前镜像依赖上游 PaddleX / PaddlePaddle 基础镜像架构支持
# 不在这里伪造 arm64 构建逻辑，构建前必须先检查基础镜像 manifest
RUN echo "Building MetaAI PaddleX OCR for TARGETPLATFORM=${TARGETPLATFORM}"

# 安装 PaddleX Basic Serving 依赖
# 依赖在构建阶段固定进镜像，避免容器启动后手工安装导致环境不可复现
# PIP_INDEX_URL 用于覆盖基础镜像内置 pip 源，避免部分镜像源返回 403 导致构建失败
RUN paddlex --install serving

EXPOSE 8080

# OCR 服务默认监听 8080，Java 侧 metax.ai.rag.ocr.base-url 指向该端口
# 第一版固定 CPU，GPU 镜像和 --device gpu:0 后续按部署机器单独扩展
CMD ["paddlex", "--serve", "--pipeline", "OCR", "--host", "0.0.0.0", "--port", "8080", "--device", "cpu"]
