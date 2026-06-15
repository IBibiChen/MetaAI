#!/usr/bin/env bash
set -euo pipefail

# 运行期入口直接以前台方式启动 FunASR 官方 2pass WebSocket 服务
# 不调用 run_server_2pass.sh，因为该脚本会把服务放到后台后立即返回，导致容器 PID 1 退出
runtime_root=/workspace/FunASR/runtime
server_bin="${runtime_root}/websocket/build/bin/funasr-wss-server-2pass"

cd "${runtime_root}"

# 默认绑定中文实时识别所需的离线 ASR、在线 ASR、VAD、标点、语言模型和 ITN 模型
# 这些参数都支持通过环境变量覆盖，便于后续替换微调模型或调整服务端热词文件
decoder_thread_num="${FUNASR_DECODER_THREAD_NUM:-$(grep -c "processor" /proc/cpuinfo || echo 32)}"
io_thread_num="${FUNASR_IO_THREAD_NUM:-$(((decoder_thread_num + 15) / 16))}"
model_thread_num="${FUNASR_MODEL_THREAD_NUM:-1}"
certfile="${FUNASR_CERTFILE:-0}"
keyfile="${FUNASR_KEYFILE:-}"

if [ -z "${certfile}" ] || [ "${certfile}" = "0" ]; then
  certfile=""
  keyfile=""
fi

args=(
  --download-model-dir "${FUNASR_MODEL_ROOT:-/workspace/models}"
  --model-dir "${FUNASR_MODEL_DIR:-damo/speech_paraformer-large_asr_nat-zh-cn-16k-common-vocab8404-onnx}"
  --online-model-dir "${FUNASR_ONLINE_MODEL_DIR:-damo/speech_paraformer-large_asr_nat-zh-cn-16k-common-vocab8404-online-onnx}"
  --vad-dir "${FUNASR_VAD_DIR:-damo/speech_fsmn_vad_zh-cn-16k-common-onnx}"
  --punc-dir "${FUNASR_PUNC_DIR:-damo/punc_ct-transformer_zh-cn-common-vad_realtime-vocab272727-onnx}"
  --lm-dir "${FUNASR_LM_DIR:-damo/speech_ngram_lm_zh-cn-ai-wesp-fst}"
  --itn-dir "${FUNASR_ITN_DIR:-thuduj12/fst_itn_zh}"
  --certfile "${certfile}"
  --keyfile "${keyfile}"
  --hotword "${FUNASR_HOTWORD:-/workspace/models/hotwords.txt}"
  --port "${FUNASR_PORT:-10095}"
  --decoder-thread-num "${decoder_thread_num}"
  --io-thread-num "${io_thread_num}"
  --model-thread-num "${model_thread_num}"
)

# 使用 exec 让 FunASR 服务接管 PID 1
# 这样 docker stop 的信号可以直接传递给服务进程，避免容器停止时留下子进程
exec "${server_bin}" "${args[@]}"
