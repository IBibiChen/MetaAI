import {fetchEventSource} from '@microsoft/fetch-event-source'

import {request, unwrapResult} from './request'

import type {
    MetaChatHistory,
    ChatOptions,
    ChatStreamDelta,
    ChatStreamDone,
    ChatStreamError,
    ChatStreamMeta,
    CommonResult,
    MetaChatFileItem,
    MetaChat,
    PageResult,
    ChatMessageResponse,
    RetrievalChatResponse,
} from '@/types/api'

// 本地开发期 API Key，必须与后端 metax.ai.security.api-key 保持一致
const METAX_API_TOKEN = 'sk-metax-123456'
const METAX_AUTHORIZATION = `Bearer ${METAX_API_TOKEN}`

export interface ChatStreamHandlers {
    onMeta?: (payload: ChatStreamMeta) => void
    onDelta?: (payload: ChatStreamDelta) => void
    onDone?: (payload: ChatStreamDone) => void
    onError?: (payload: ChatStreamError) => void
    onNetworkError?: (event: Event) => void
}

export interface ChatStreamHandle {
    close: () => void
}

/**
 * 发送普通聊天
 *
 * <p>
 * 对应后端 GET /v1/chat
 * 该接口返回 answer、chatId 和本次参与上下文的 files，不包 CommonResult
 *
 * @example
 * const answer = await sendPlainChat('t1-u1-s1', 't1', 'u1', '你是谁')
 */
export async function sendPlainChat(chatId: string, tenantId: string, userId: string, msg: string, fileIds: string[] = []) {
    const response = await request.get<CommonResult<ChatMessageResponse>>('/v1/chat', {
        params: {
            chatId,
            tenantId,
            userId,
            msg,
            stream: 'false',
            fileIds: toCsv(fileIds),
            contextScope: fileIds.length > 0 ? 'FILES_ONLY' : undefined,
        },
    })
    return unwrapResult(response.data)
}

/**
 * 发送普通 JSON 聊天
 *
 * <p>
 * 对应后端 POST /v1/chat
 * 使用 JSON body 携带复杂参数和 Authorization Header
 * fileIds 为空时本轮不使用会话文件
 */
export async function sendPlainChatJson(options: ChatOptions, fileIds: string[] = []) {
    const response = await request.post<CommonResult<ChatMessageResponse>>('/v1/chat', removeEmptyFields({
        chatId: options.chatId,
        tenantId: options.tenantId,
        userId: options.userId,
        msg: options.msg,
        stream: false,
        fileIds,
        contextScope: options.contextScope || (fileIds.length > 0 ? 'FILES_ONLY' : undefined),
    }), {
        headers: {
            Authorization: METAX_AUTHORIZATION,
        },
    })
    return unwrapResult(response.data)
}

/**
 * 上传会话临时文件
 *
 * <p>
 * 文件只绑定当前 chatId，后续问答必须通过 fileIds 显式选择
 * 上传接口是唯一保留 multipart/form-data 的聊天文件入口
 */
export async function uploadChatFiles(chatId: string, tenantId: string, userId: string, files: File[]) {
    const formData = new FormData()
    formData.append('chatId', chatId)
    formData.append('tenantId', tenantId)
    formData.append('userId', userId)
    files.forEach((file) => formData.append('files', file))

    const response = await request.post<CommonResult<MetaChatFileItem[]>>('/v1/chat/files', formData, {
        headers: {
            Authorization: METAX_AUTHORIZATION,
        },
    })
    return unwrapResult(response.data)
}

/**
 * 查询当前会话临时文件
 */
export async function fetchChatFiles(chatId: string, tenantId: string, userId: string) {
    const response = await request.get<CommonResult<MetaChatFileItem[]>>('/v1/chat/files', {
        params: {
            chatId,
            tenantId,
            userId,
        },
    })
    return unwrapResult(response.data)
}

/**
 * 发送普通聊天流
 *
 * <p>
 * 对应后端 GET /v1/chat?stream=true
 * 使用 EventSource 消费 SSE 事件
 */
export function streamPlainChat(
    chatId: string,
    tenantId: string,
    userId: string,
    msg: string,
    handlers: ChatStreamHandlers,
    fileIds: string[] = [],
) {
    return openChatStream('/api/v1/chat', {
        chatId,
        tenantId,
        userId,
        msg,
        stream: 'true',
        fileIds: toCsv(fileIds),
        contextScope: fileIds.length > 0 ? 'FILES_ONLY' : undefined,
    }, handlers)
}

/**
 * 发送普通聊天 JSON 流
 *
 * <p>
 * 对应后端 POST /v1/chat
 * 使用 fetchEventSource 携带 JSON body 和 Authorization Header
 * 适合带 fileIds 或需要鉴权 Header 的复杂流式问答
 */
export function streamPlainChatJson(options: ChatOptions, fileIds: string[], handlers: ChatStreamHandlers) {
    return openJsonChatStream('/api/v1/chat', {
        chatId: options.chatId,
        tenantId: options.tenantId,
        userId: options.userId,
        msg: options.msg,
        stream: true,
        fileIds,
        contextScope: options.contextScope || (fileIds.length > 0 ? 'FILES_ONLY' : undefined),
    }, handlers)
}

/**
 * 发送 RAG 聊天
 *
 * <p>
 * 对应后端 GET /v1/rag
 * 返回 answer 和文件引用 references
 */
export async function sendRagChat(options: ChatOptions) {
    const response = await request.get<CommonResult<RetrievalChatResponse>>('/v1/rag', {
        params: {
            chatId: options.chatId,
            msg: options.msg,
            tenantId: options.tenantId,
            kbId: options.kbId,
            stream: 'false',
            documentId: options.documentId || undefined,
            documentType: options.documentType || undefined,
            userId: options.userId || undefined,
            deptIds: options.deptIds || undefined,
            fileIds: toCsv(options.fileIds),
            contextScope: options.contextScope || resolveRagContextScope(options.fileIds || []),
        },
    })
    return unwrapResult(response.data)
}

/**
 * 发送 RAG JSON 聊天
 *
 * <p>
 * 对应后端 POST /v1/rag
 * 返回 answer、references 和本次参与上下文的 files
 * fileIds 仅作为会话文件上下文，不会进入知识库 references
 */
export async function sendRagChatJson(options: ChatOptions, fileIds: string[] = []) {
    const response = await request.post<CommonResult<RetrievalChatResponse>>('/v1/rag', removeEmptyFields({
        chatId: options.chatId,
        msg: options.msg,
        tenantId: options.tenantId,
        kbId: options.kbId,
        stream: false,
        documentId: options.documentId || undefined,
        documentType: options.documentType || undefined,
        userId: options.userId,
        deptIds: options.deptIds || undefined,
        fileIds,
        contextScope: options.contextScope || resolveRagContextScope(fileIds),
    }), {
        headers: {
            Authorization: METAX_AUTHORIZATION,
        },
    })
    return unwrapResult(response.data)
}

/**
 * 发送 RAG 聊天流
 *
 * <p>
 * 对应后端 GET /v1/rag?stream=true
 * done 事件返回完整 answer 和 references
 */
export function streamRagChat(options: ChatOptions, handlers: ChatStreamHandlers) {
    return openChatStream('/api/v1/rag', {
        chatId: options.chatId,
        msg: options.msg,
        tenantId: options.tenantId,
        kbId: options.kbId,
        stream: 'true',
        documentId: options.documentId,
        documentType: options.documentType,
        userId: options.userId,
        deptIds: options.deptIds,
        fileIds: toCsv(options.fileIds),
        contextScope: options.contextScope || resolveRagContextScope(options.fileIds || []),
    }, handlers)
}

/**
 * 发送 RAG JSON 聊天流
 *
 * <p>
 * 对应后端 POST /v1/rag
 * done 事件返回完整 answer、references 和 files
 * references 来自知识库检索，files 来自会话文件上下文
 */
export function streamRagChatJson(options: ChatOptions, fileIds: string[], handlers: ChatStreamHandlers) {
    return openJsonChatStream('/api/v1/rag', {
        chatId: options.chatId,
        msg: options.msg,
        tenantId: options.tenantId,
        kbId: options.kbId,
        stream: true,
        documentId: options.documentId || undefined,
        documentType: options.documentType || undefined,
        userId: options.userId,
        deptIds: options.deptIds || undefined,
        fileIds,
        contextScope: options.contextScope || resolveRagContextScope(fileIds),
    }, handlers)
}

/**
 * 解析 RAG 默认回答范围
 *
 * <p>
 * 有显式附件时默认只基于附件回答，无附件时默认只使用知识库
 */
function resolveRagContextScope(fileIds: string[] = []) {
    return fileIds.length > 0 ? 'FILES_ONLY' : 'KNOWLEDGE_ONLY'
}

/**
 * 按 chatId 查询聊天历史
 *
 * <p>
 * 点击左侧会话列表时优先使用 chatId 加载完整消息
 */
export async function fetchChatHistoryByChatId(chatId: string, current = 1, size = 200) {
    const response = await request.get<CommonResult<PageResult<MetaChatHistory>>>('/v1/chat/history/page', {
        params: {
            chatId,
            current,
            size,
        },
    })
    return unwrapResult(response.data)
}

/**
 * 查询聊天会话列表
 */
export async function fetchChats(tenantId: string, userId: string, current = 1, size = 30) {
    const response = await request.get<CommonResult<PageResult<MetaChat>>>('/v1/chats/page', {
        params: {
            tenantId,
            userId,
            current,
            size,
        },
    })
    return unwrapResult(response.data)
}

/**
 * 重命名聊天会话
 */
export async function renameChat(id: string, title: string) {
    const response = await request.patch<CommonResult<MetaChat>>(`/v1/chats/${id}/title`, {
        title,
    })
    return unwrapResult(response.data)
}

/**
 * 更新聊天会话状态
 */
export async function updateChatFlags(id: string, flags: Partial<Pick<MetaChat, 'pinned' | 'favorite' | 'archived'>>) {
    const response = await request.patch<CommonResult<MetaChat>>(`/v1/chats/${id}/flags`, flags)
    return unwrapResult(response.data)
}

/**
 * 软删除聊天会话
 */
export async function deleteChat(id: string) {
    const response = await request.delete<CommonResult<void>>(`/v1/chats/${id}`)
    return unwrapResult(response.data)
}

/**
 * 把文件 ID 数组转换成 GET query 可传递的逗号分隔文本
 */
function toCsv(values: string[] | undefined) {
    return values && values.length ? values.join(',') : undefined
}

/**
 * 打开 GET SSE 流
 *
 * <p>
 * 使用浏览器原生 EventSource，不携带 Authorization Header
 */
function openChatStream(path: string, params: Record<string, string | undefined>, handlers: ChatStreamHandlers): ChatStreamHandle {
    const url = `${path}?${toSearchParams(params)}`
    const eventSource = new EventSource(url)
    let completed = false

    eventSource.addEventListener('meta', (event) => {
        handlers.onMeta?.(parseStreamPayload<ChatStreamMeta>(event))
    })
    eventSource.addEventListener('delta', (event) => {
        handlers.onDelta?.(parseStreamPayload<ChatStreamDelta>(event))
    })
    eventSource.addEventListener('done', (event) => {
        completed = true
        handlers.onDone?.(parseStreamPayload<ChatStreamDone>(event))
        eventSource.close()
    })
    eventSource.addEventListener('error', (event) => {
        if (hasMessageData(event)) {
            completed = true
            handlers.onError?.(parseStreamPayload<ChatStreamError>(event))
            eventSource.close()
            return
        }
        if (!completed) {
            handlers.onNetworkError?.(event)
            eventSource.close()
        }
    })

    return {
        close: () => eventSource.close(),
    }
}

/**
 * 打开 POST JSON SSE 流
 *
 * <p>
 * 使用 fetchEventSource 发送 JSON 请求体和 Authorization Header
 * AbortController 同时负责停止生成和 done / error 后主动关闭连接
 */
function openJsonChatStream(path: string, body: Record<string, unknown>, handlers: ChatStreamHandlers): ChatStreamHandle {
    const controller = new AbortController()
    let completed = false

    void fetchEventSource(path, {
        method: 'POST',
        headers: {
            Authorization: METAX_AUTHORIZATION,
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(removeEmptyFields(body)),
        signal: controller.signal,
        openWhenHidden: true,
        async onopen(response) {
            if (response.ok) {
                return
            }
            // 非 2xx 响应先解析后端错误体，便于页面展示明确失败原因
            const messageText = await errorMessage(response)
            throw new Error(messageText)
        },
        onmessage(event) {
            // 后端统一返回 meta、delta、done、error 四类 SSE 事件
            if (event.event === 'meta') {
                handlers.onMeta?.(parseJsonPayload<ChatStreamMeta>(event.data))
                return
            }
            if (event.event === 'delta') {
                handlers.onDelta?.(parseJsonPayload<ChatStreamDelta>(event.data))
                return
            }
            if (event.event === 'done') {
                completed = true
                handlers.onDone?.(parseJsonPayload<ChatStreamDone>(event.data))
                // done 表示业务流已经完成，立即 abort 避免连接继续占用资源
                controller.abort()
                return
            }
            if (event.event === 'error') {
                completed = true
                handlers.onError?.(parseJsonPayload<ChatStreamError>(event.data))
                controller.abort()
            }
        },
        onerror(error) {
            if (controller.signal.aborted || completed) {
                return
            }
            // 只有非主动 abort 的异常才交给页面网络错误处理
            handlers.onNetworkError?.(new ErrorEvent('error', {
                message: error instanceof Error ? error.message : '流式连接失败',
                error,
            }))
            throw error
        },
    }).catch(() => undefined)

    return {
        close: () => controller.abort(),
    }
}

/**
 * 构造 GET SSE query string
 *
 * <p>
 * 空值不进入 query，避免后端接收到无意义过滤参数
 */
function toSearchParams(params: Record<string, string | undefined>) {
    const searchParams = new URLSearchParams()
    Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== '') {
            searchParams.set(key, value)
        }
    })
    return searchParams.toString()
}

/**
 * 解析 EventSource 原生事件数据
 */
function parseStreamPayload<T>(event: Event): T {
    if (!hasMessageData(event)) {
        throw new Error('流式事件数据为空')
    }
    return JSON.parse(event.data) as T
}

/**
 * 解析 fetchEventSource JSON 事件数据
 */
function parseJsonPayload<T>(data: string): T {
    if (!data) {
        throw new Error('流式事件数据为空')
    }
    return JSON.parse(data) as T
}

/**
 * 判断事件是否携带字符串 data
 */
function hasMessageData(event: Event): event is MessageEvent<string> {
    return 'data' in event && typeof (event as MessageEvent<string>).data === 'string'
}

/**
 * 移除 JSON 请求体中的空字段
 *
 * <p>
 * 避免把空字符串、null、undefined 或空数组传给后端，影响检索范围和 fileIds 语义
 */
function removeEmptyFields(body: Record<string, unknown>) {
    return Object.fromEntries(Object.entries(body).filter(([, value]) => {
        if (value === undefined || value === null || value === '') {
            return false
        }
        return !(Array.isArray(value) && value.length === 0)
    }))
}

/**
 * 提取 HTTP 错误响应文本
 *
 * <p>
 * 后端通常返回 CommonResult 结构，解析失败时退回 HTTP 状态码
 */
async function errorMessage(response: Response) {
    try {
        const payload = await response.json() as { message?: string }
        return payload.message || `HTTP ${response.status}`
    } catch {
        return `HTTP ${response.status}`
    }
}
