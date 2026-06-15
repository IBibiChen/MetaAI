# 固定使用 FunASR 官方实时 CPU 0.1.13
# 该 tag 已验证同时包含 linux/amd64 和 linux/arm64，适合发布双平台 CPU manifest
ARG BASE_IMAGE=registry.cn-hangzhou.aliyuncs.com/funasr_repo/funasr:funasr-runtime-sdk-online-cpu-0.1.13
FROM ${BASE_IMAGE}

# 这些默认值同时用于构建期模型预下载和运行期服务启动
# 运行期可以通过同名环境变量覆盖模型、端口、热词和 SSL 设置
ENV FUNASR_MODEL_ROOT=/workspace/models \
    FUNASR_PORT=10095 \
    FUNASR_MODEL_DIR=damo/speech_paraformer-large_asr_nat-zh-cn-16k-common-vocab8404-onnx \
    FUNASR_ONLINE_MODEL_DIR=damo/speech_paraformer-large_asr_nat-zh-cn-16k-common-vocab8404-online-onnx \
    FUNASR_VAD_DIR=damo/speech_fsmn_vad_zh-cn-16k-common-onnx \
    FUNASR_PUNC_DIR=damo/punc_ct-transformer_zh-cn-common-vad_realtime-vocab272727-onnx \
    FUNASR_LM_DIR=damo/speech_ngram_lm_zh-cn-ai-wesp-fst \
    FUNASR_ITN_DIR=thuduj12/fst_itn_zh \
    FUNASR_HOTWORD=/workspace/models/hotwords.txt \
    FUNASR_CERTFILE=0

# 预置空热词文件，避免 run_server_2pass.sh 启动时引用不存在的路径
# 生产环境如需服务端全局热词，可以挂载文件并覆盖 FUNASR_HOTWORD
COPY hotwords.txt /workspace/models/hotwords.txt

# entrypoint 只负责运行期启动服务，构建期模型下载由 Dockerfile 中的 snapshot_download 完成
COPY --chmod=755 docker-entrypoint.sh /usr/local/bin/meta-funasr-entrypoint

# 构建期显式下载 ModelScope 模型到 /workspace/models
# 不使用 run_server_2pass.sh 预热模型，因为该脚本会把服务放到后台后立即返回，构建阶段无法可靠等待下载完成
RUN set -e; \
    mkdir -p "${FUNASR_MODEL_ROOT}"; \
    python3 -c "from modelscope import snapshot_download; model_root='${FUNASR_MODEL_ROOT}'; model_ids=['damo/speech_paraformer-large_asr_nat-zh-cn-16k-common-vocab8404-onnx','damo/speech_paraformer-large_asr_nat-zh-cn-16k-common-vocab8404-online-onnx','damo/speech_fsmn_vad_zh-cn-16k-common-onnx','damo/punc_ct-transformer_zh-cn-common-vad_realtime-vocab272727-onnx','damo/speech_ngram_lm_zh-cn-ai-wesp-fst','thuduj12/fst_itn_zh']; [(print(f'Downloading {model_id} to {model_root}', flush=True), snapshot_download(model_id, cache_dir=model_root)) for model_id in model_ids]"

EXPOSE 10095

ENTRYPOINT ["/usr/local/bin/meta-funasr-entrypoint"]
