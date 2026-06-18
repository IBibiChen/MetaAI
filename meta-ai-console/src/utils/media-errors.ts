const VOICE_INPUT_STARTUP_FALLBACK = '语音输入启动失败，请检查麦克风设备和浏览器权限'

const MEDIA_ERROR_MESSAGES: Record<string, string> = {
    NotFoundError: '未检测到可用麦克风，请连接或启用输入设备后重试',
    DevicesNotFoundError: '未检测到可用麦克风，请连接或启用输入设备后重试',
    NotAllowedError: '麦克风权限被拒绝，请在浏览器权限设置中允许后重试',
    PermissionDeniedError: '麦克风权限被拒绝，请在浏览器权限设置中允许后重试',
    SecurityError: '麦克风权限被拒绝，请在浏览器权限设置中允许后重试',
    NotReadableError: '麦克风被其他应用占用或无法读取，请关闭占用程序后重试',
    TrackStartError: '麦克风被其他应用占用或无法读取，请关闭占用程序后重试',
    OverconstrainedError: '当前麦克风不满足录音要求，请更换输入设备后重试',
    ConstraintNotSatisfiedError: '当前麦克风不满足录音要求，请更换输入设备后重试',
    AbortError: '麦克风启动被中断，请重试',
}

/**
 * 把语音输入启动异常转换为用户可读中文文案
 *
 * <p>
 * 浏览器 getUserMedia 会按浏览器实现返回不同的 DOMException name 或英文 message
 *
 * @param error 语音输入启动链路抛出的异常
 * @return 用户可读中文错误提示
 */
export function formatVoiceInputStartupError(error: unknown) {
    if (!(error instanceof Error)) {
        return VOICE_INPUT_STARTUP_FALLBACK
    }

    const namedMessage = MEDIA_ERROR_MESSAGES[error.name]
    if (namedMessage) {
        return namedMessage
    }

    const lowerMessage = error.message.toLowerCase()
    if (lowerMessage.includes('requested device not found') || lowerMessage.includes('device not found')) {
        return MEDIA_ERROR_MESSAGES.NotFoundError
    }
    if (lowerMessage.includes('permission denied') || lowerMessage.includes('permission dismissed')) {
        return MEDIA_ERROR_MESSAGES.NotAllowedError
    }
    if (lowerMessage.includes('could not start') || lowerMessage.includes('not readable')) {
        return MEDIA_ERROR_MESSAGES.NotReadableError
    }
    if (lowerMessage.includes('constraint')) {
        return MEDIA_ERROR_MESSAGES.OverconstrainedError
    }

    return /[\u4e00-\u9fa5]/.test(error.message) ? error.message : VOICE_INPUT_STARTUP_FALLBACK
}
