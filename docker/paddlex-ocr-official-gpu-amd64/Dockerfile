ARG TARGETPLATFORM
ARG PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple
ARG PIP_TRUSTED_HOST=mirrors.aliyun.com

FROM ccr-2vdh3abv-pub.cnc.bj.baidubce.com/paddlex/paddlex:paddlex3.3.11-paddlepaddle3.2.0-cpu AS model-cache

ARG PIP_INDEX_URL
ARG PIP_TRUSTED_HOST

ENV PIP_INDEX_URL=${PIP_INDEX_URL}
ENV PIP_TRUSTED_HOST=${PIP_TRUSTED_HOST}
ENV PIP_DISABLE_PIP_VERSION_CHECK=1
ENV DISABLE_MODEL_SOURCE_CHECK=True

# 使用 CPU 官方镜像预热 OCR 模型缓存
# 避免 GPU 镜像构建阶段导入 Paddle GPU core 时缺少 libcuda.so.1
RUN paddlex --install serving

RUN PYTHON_BIN="$(command -v python3 || command -v python)" \
    && "${PYTHON_BIN}" -c "from paddlex import create_pipeline; pipeline = create_pipeline('OCR'); print(type(pipeline).__name__)"

RUN test -d /root/.paddlex && find /root/.paddlex -maxdepth 3 -type f | head

FROM ccr-2vdh3abv-pub.cnc.bj.baidubce.com/paddlex/paddlex:paddlex3.3.11-paddlepaddle3.2.0-gpu-cuda11.8-cudnn8.9-trt8.6

ARG TARGETPLATFORM
ARG PIP_INDEX_URL
ARG PIP_TRUSTED_HOST

ENV PIP_INDEX_URL=${PIP_INDEX_URL}
ENV PIP_TRUSTED_HOST=${PIP_TRUSTED_HOST}
ENV PIP_DISABLE_PIP_VERSION_CHECK=1
ENV DISABLE_MODEL_SOURCE_CHECK=True
ENV NVIDIA_VISIBLE_DEVICES=all
ENV NVIDIA_DRIVER_CAPABILITIES=compute,utility

LABEL org.opencontainers.image.title="MetaAI PaddleX OCR Official GPU AMD64"
LABEL org.opencontainers.image.description="Official PaddleX GPU OCR Basic Serving image for linux/amd64"

# 当前镜像基于 PaddleX 官方 GPU 镜像，仅按 linux/amd64 发布
RUN echo "Building MetaAI official PaddleX OCR GPU image for TARGETPLATFORM=${TARGETPLATFORM}"

# 安装 PaddleX Basic Serving 依赖
# 官方 PaddleX GPU 镜像已预装 CUDA / cuDNN / TensorRT / PaddleX / PaddlePaddle
RUN paddlex --install serving

# 复制 CPU 预热阶段下载好的 OCR 模型缓存
# final GPU stage 不初始化 predictor，避免构建期依赖宿主机 NVIDIA 驱动
COPY --from=model-cache /root/.paddlex /root/.paddlex

# 检查模型缓存目录，避免构建出没有预置模型的离线镜像
RUN test -d /root/.paddlex && find /root/.paddlex -maxdepth 3 -type f | head

EXPOSE 8080

# GPU 版本固定使用 gpu:0，容器运行时必须通过 --gpus all 挂载 NVIDIA 设备
CMD ["paddlex", "--serve", "--pipeline", "OCR", "--host", "0.0.0.0", "--port", "8080", "--device", "gpu:0"]
