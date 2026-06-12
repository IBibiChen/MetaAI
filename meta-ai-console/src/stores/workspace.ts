import {defineStore} from 'pinia'
import type {MetaChat} from '@/types/api'

const DEFAULT_TENANT_ID = 't1'
const DEFAULT_KB_ID = 'kb1'
const DEFAULT_USER_ID = 'u1'
const DEFAULT_DEPT_IDS = ''

/**
 * 创建短会话后缀
 *
 * <p>
 * 后缀只用于区分同一租户、同一用户下的多次独立对话
 */
function createSessionSuffix() {
    return crypto.randomUUID ? crypto.randomUUID().slice(0, 8) : String(Date.now())
}

/**
 * 创建完整会话 ID
 *
 * <p>
 * chatId 是独立业务会话 ID，不再从既有 chatId 中反向解析 tenantId 或 userId
 */
function createChatId(tenantId: string, userId: string) {
    return `${tenantId}-${userId}-${createSessionSuffix()}`
}

/**
 * 读取安全的本地缓存值
 *
 * <p>
 * 旧版本可能把冒号格式 chatId 写入 tenantId 或 chatId，启动时直接丢弃这类开发期脏值
 */
function safeStorageValue(key: string, fallback: string, rejectColon = false) {
    const value = localStorage.getItem(key)
    if (!value || (rejectColon && value.includes(':'))) {
        return fallback
    }
    return value
}

/**
 * 全局工作区上下文
 *
 * <p>
 * tenantId、kbId、userId 和 deptIds 会被文件管理与 RAG 聊天复用
 * chatId 作为独立会话 ID 保存，不能再拆分反推租户和用户
 */
export const useWorkspaceStore = defineStore('workspace', {
    state: () => {
        const tenantId = safeStorageValue('meta-ai.tenantId', DEFAULT_TENANT_ID, true)
        const userId = safeStorageValue('meta-ai.userId', DEFAULT_USER_ID, true)
        const cachedChatId = safeStorageValue('meta-ai.chatId', '', true)
        return {
            tenantId,
            kbId: safeStorageValue('meta-ai.kbId', DEFAULT_KB_ID),
            userId,
            deptIds: safeStorageValue('meta-ai.deptIds', DEFAULT_DEPT_IDS),
            chatId: cachedChatId || createChatId(tenantId, userId),
            contextVersion: 0,
        }
    },
    actions: {
        /**
         * 持久化工作区上下文
         *
         * <p>
         * 页面刷新后仍然保留上一次使用的上下文
         */
        persist() {
            localStorage.setItem('meta-ai.tenantId', this.tenantId)
            localStorage.setItem('meta-ai.kbId', this.kbId)
            localStorage.setItem('meta-ai.userId', this.userId)
            localStorage.setItem('meta-ai.deptIds', this.deptIds)
            localStorage.setItem('meta-ai.chatId', this.chatId)
            localStorage.removeItem('meta-ai.sessionId')
        },
        /**
         * 创建新的聊天会话
         *
         * <p>
         * 当前租户、知识库和用户不变，只生成新的完整 chatId
         */
        resetSession() {
            this.chatId = createChatId(this.tenantId, this.userId)
            this.persist()
        },
        /**
         * 应用顶部调试区上下文
         *
         * <p>
         * 手动切换用户、租户或知识库时创建新会话
         * contextVersion 用于通知聊天页清理当前窗口并刷新会话列表
         */
        applyContext() {
            this.chatId = createChatId(this.tenantId, this.userId)
            this.contextVersion += 1
            this.persist()
        },
        /**
         * 重置顶部调试区默认值
         *
         * <p>
         * 恢复默认租户、用户、部门和知识库，同时创建新会话
         */
        resetDefaults() {
            this.tenantId = DEFAULT_TENANT_ID
            this.kbId = DEFAULT_KB_ID
            this.userId = DEFAULT_USER_ID
            this.deptIds = DEFAULT_DEPT_IDS
            this.chatId = createChatId(this.tenantId, this.userId)
            this.contextVersion += 1
            this.persist()
        },
        /**
         * 应用 iframe 嵌入上下文
         *
         * <p>
         * 业务系统通过 URL 传入租户、用户和知识库上下文
         * 上下文变化时创建新的聊天会话，避免沿用其他用户的 chatId
         *
         * @param context 嵌入页传入的业务上下文
         */
        applyEmbeddedContext(context: {
            tenantId?: string
            userId?: string
            kbId?: string
            deptIds?: string
        }) {
            const nextTenantId = context.tenantId || this.tenantId
            const nextUserId = context.userId || this.userId
            const nextKbId = context.kbId || this.kbId
            const nextDeptIds = context.deptIds ?? this.deptIds
            const changed = nextTenantId !== this.tenantId
                || nextUserId !== this.userId
                || nextKbId !== this.kbId
                || nextDeptIds !== this.deptIds

            this.tenantId = nextTenantId
            this.userId = nextUserId
            this.kbId = nextKbId
            this.deptIds = nextDeptIds

            if (changed) {
                this.chatId = createChatId(this.tenantId, this.userId)
                this.contextVersion += 1
            }
            this.persist()
        },
        /**
         * 选择已有聊天会话
         *
         * <p>
         * 使用会话主表的独立字段同步工作区，严禁从 chatId 中拆分 tenantId 或 userId
         */
        selectChat(chat: Pick<MetaChat, 'tenantId' | 'userId' | 'chatId'>) {
            this.tenantId = chat.tenantId
            this.userId = chat.userId
            this.chatId = chat.chatId
            this.persist()
        },
        /**
         * 同步后端解析后的会话 ID
         *
         * <p>
         * 流式 meta 事件只更新 chatId，不允许改写 tenantId 或 userId
         */
        setChatId(chatId: string) {
            if (!chatId || chatId.includes(':')) {
                return
            }
            this.chatId = chatId
            this.persist()
        },
    },
})
