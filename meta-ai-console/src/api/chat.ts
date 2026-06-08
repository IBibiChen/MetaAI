import {request, unwrapResult} from './request'

import type {
    MetaChatHistory,
    ChatOptions,
    ChatStreamDelta,
    ChatStreamDone,
    ChatStreamError,
    ChatStreamMeta,
    CommonResult,
    MetaChat,
    PageResult,
    RetrievalChatResponse,
} from '@/types/api'

export interface ChatStreamHandlers {
    onMeta?: (payload: ChatStreamMeta) => void
    onDelta?: (payload: ChatStreamDelta) => void
    onDone?: (payload: ChatStreamDone) => void
    onError?: (payload: ChatStreamError) => void
    onNetworkError?: (event: Event) => void
}

/**
 * 发送普通聊天
 *
 * <p>
 * 对应后端 GET /v1/chat
 * 该接口直接返回字符串，不包 CommonResult
 *
 * @example
 * const answer = await sendPlainChat('t1:u1:s1', 't1', 'u1', '你是谁')
 */
export async function sendPlainChat(chatId: string, tenantId: string, userId: string, msg: string) {
    const response = await request.get<string>('/v1/chat', {
        params: {
            chatId,
            tenantId,
            userId,
            msg,
        },
    })
    return response.data
}

/**
 * 发送普通聊天流
 *
 * <p>
 * 对应后端 GET /v1/chat/stream
 * 使用 EventSource 消费 SSE 事件
 */
export function streamPlainChat(
    chatId: string,
    tenantId: string,
    userId: string,
    msg: string,
    handlers: ChatStreamHandlers,
) {
    return openChatStream('/api/v1/chat/stream', {
        chatId,
        tenantId,
        userId,
        msg,
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
    const response = await request.get<RetrievalChatResponse>('/v1/rag', {
        params: {
            chatId: options.chatId,
            msg: options.msg,
            tenantId: options.tenantId,
            kbId: options.kbId,
            documentId: options.documentId || undefined,
            documentType: options.documentType || undefined,
            userId: options.userId || undefined,
            deptIds: options.deptIds || undefined,
        },
    })
    return response.data
}

/**
 * 发送 RAG 聊天流
 *
 * <p>
 * 对应后端 GET /v1/rag/stream
 * done 事件返回完整 answer 和 references
 */
export function streamRagChat(options: ChatOptions, handlers: ChatStreamHandlers) {
    return openChatStream('/api/v1/rag/stream', {
        chatId: options.chatId,
        msg: options.msg,
        tenantId: options.tenantId,
        kbId: options.kbId,
        documentId: options.documentId,
        documentType: options.documentType,
        userId: options.userId,
        deptIds: options.deptIds,
    }, handlers)
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

function openChatStream(path: string, params: Record<string, string | undefined>, handlers: ChatStreamHandlers) {
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

    return eventSource
}

function toSearchParams(params: Record<string, string | undefined>) {
    const searchParams = new URLSearchParams()
    Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== '') {
            searchParams.set(key, value)
        }
    })
    return searchParams.toString()
}

function parseStreamPayload<T>(event: Event): T {
    if (!hasMessageData(event)) {
        throw new Error('流式事件数据为空')
    }
    return JSON.parse(event.data) as T
}

function hasMessageData(event: Event): event is MessageEvent<string> {
    return 'data' in event && typeof (event as MessageEvent<string>).data === 'string'
}
