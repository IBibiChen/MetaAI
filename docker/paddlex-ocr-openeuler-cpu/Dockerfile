FROM openeuler/paddlepaddle:3.2.0-oe2403sp2

ARG TARGETPLATFORM
ARG PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple
ARG PIP_TRUSTED_HOST=mirrors.aliyun.com
ARG PADDLEX_VERSION=3.3.11

ENV PIP_INDEX_URL=${PIP_INDEX_URL}
ENV PIP_TRUSTED_HOST=${PIP_TRUSTED_HOST}
ENV PIP_DISABLE_PIP_VERSION_CHECK=1
ENV DISABLE_MODEL_SOURCE_CHECK=True

LABEL org.opencontainers.image.title="MetaAI PaddleX OCR openEuler CPU"
LABEL org.opencontainers.image.description="PaddleX OCR Basic Serving image based on openEuler PaddlePaddle for linux/amd64 and linux/arm64"

# 该镜像基于 openEuler PaddlePaddle 多架构基础镜像构建
# 同一 Dockerfile 用于 linux/amd64 和 linux/arm64 CPU 镜像
RUN echo "Building MetaAI PaddleX OCR openEuler CPU image for TARGETPLATFORM=${TARGETPLATFORM}"

# 安装 PaddleX / OpenCV 运行时依赖
# libglvnd-glx 提供 libGL.so.1，避免 cv2 导入失败
RUN dnf install -y \
        libglvnd-glx \
        libXext \
        libSM \
        libXrender \
        fontconfig \
    && dnf clean all \
    && rm -rf /var/cache/dnf

# 固定 PaddleX OCR 与 Basic Serving 依赖
# paddlex --install serving 只安装服务化依赖，OCR pipeline 依赖由 paddlex[ocr] 提供
RUN python3 --version \
    && python3 -m pip --version \
    && python3 -m pip install "paddlex[ocr]==${PADDLEX_VERSION}" \
    && paddlex --install serving

# 预热 OCR pipeline 模型缓存
# 离线部署时依赖该层把模型打入镜像，运行期不再联网下载模型
RUN python3 -c "from paddlex import create_pipeline; pipeline = create_pipeline('OCR'); print(type(pipeline).__name__)"

# 检查模型缓存目录，避免构建出没有预置模型的离线镜像
RUN test -d /root/.paddlex && find /root/.paddlex -maxdepth 3 -type f | head

EXPOSE 8080

# 保持和 Java 侧 PaddleOcrClient 一致的 /ocr JSON 协议
CMD ["paddlex", "--serve", "--pipeline", "OCR", "--host", "0.0.0.0", "--port", "8080", "--device", "cpu"]
