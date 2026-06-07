FROM ccr-2vdh3abv-pub.cnc.bj.baidubce.com/paddlex/paddlex:paddlex3.3.11-paddlepaddle3.2.0-cpu

ARG TARGETPLATFORM
ARG PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple
ARG PIP_TRUSTED_HOST=mirrors.aliyun.com

ENV PIP_INDEX_URL=${PIP_INDEX_URL}
ENV PIP_TRUSTED_HOST=${PIP_TRUSTED_HOST}
ENV PIP_DISABLE_PIP_VERSION_CHECK=1
ENV DISABLE_MODEL_SOURCE_CHECK=True

LABEL org.opencontainers.image.title="MetaAI PaddleX OCR Official CPU AMD64"
LABEL org.opencontainers.image.description="Official PaddleX CPU OCR Basic Serving image for linux/amd64"

# 当前镜像基于 PaddleX 官方 CPU 镜像，仅按 linux/amd64 发布
RUN echo "Building MetaAI official PaddleX OCR CPU image for TARGETPLATFORM=${TARGETPLATFORM}"

# 安装 PaddleX Basic Serving 依赖
# 官方 PaddleX 镜像已预装 PaddleX / PaddlePaddle / OCR 依赖
RUN paddlex --install serving

# 预热 OCR pipeline 模型缓存
# 离线部署时依赖该层把模型打入镜像，运行期不再联网下载模型
RUN PYTHON_BIN="$(command -v python3 || command -v python)" \
    && "${PYTHON_BIN}" -c "from paddlex import create_pipeline; pipeline = create_pipeline('OCR'); print(type(pipeline).__name__)"

# 检查模型缓存目录，避免构建出没有预置模型的离线镜像
RUN test -d /root/.paddlex && find /root/.paddlex -maxdepth 3 -type f | head

EXPOSE 8080

# OCR 服务默认监听 8080，Java 侧 metax.ai.rag.ocr.base-url 指向该端口
CMD ["paddlex", "--serve", "--pipeline", "OCR", "--host", "0.0.0.0", "--port", "8080", "--device", "cpu"]
