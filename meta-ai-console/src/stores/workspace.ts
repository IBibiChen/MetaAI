import {defineStore} from 'pinia'

const DEFAULT_TENANT_ID = 't1'
const DEFAULT_KNOWLEDGE_BASE_ID = 'kb1'
const DEFAULT_USER_ID = 'u1'
const DEFAULT_DEPT_IDS = ''

/**
 * 创建短会话 ID
 *
 * <p>
 * conversationId 采用 tenantId:userId:sessionId
 * sessionId 用于区分同一用户的多次独立对话
 */
function createSessionId() {
    return crypto.randomUUID ? crypto.randomUUID().slice(0, 8) : String(Date.now())
}

/**
 * 全局工作区上下文
 *
 * <p>
 * tenantId、knowledgeBaseId、userId 和 deptIds 会被文件管理与 RAG 聊天复用
 * 当前阶段没有登录系统，因此先存到 localStorage
 */
export const useWorkspaceStore = defineStore('workspace', {
    state: () => ({
        tenantId: localStorage.getItem('meta-ai.tenantId') || DEFAULT_TENANT_ID,
        knowledgeBaseId: localStorage.getItem('meta-ai.knowledgeBaseId') || DEFAULT_KNOWLEDGE_BASE_ID,
        userId: localStorage.getItem('meta-ai.userId') || DEFAULT_USER_ID,
        deptIds: localStorage.getItem('meta-ai.deptIds') || DEFAULT_DEPT_IDS,
        sessionId: localStorage.getItem('meta-ai.sessionId') || createSessionId(),
        contextVersion: 0,
    }),
    getters: {
        /**
         * 后端推荐格式的会话 ID
         *
         * @example
         * t1:u1:9a8b7c6d
         */
        conversationId(state) {
            return `${state.tenantId}:${state.userId}:${state.sessionId}`
        },
    },
    actions: {
        /**
         * 持久化工作区上下文
         *
         * <p>
         * 顶部输入框失焦时会调用
         * 页面刷新后仍然保留上一次使用的上下文
         */
        persist() {
            localStorage.setItem('meta-ai.tenantId', this.tenantId)
            localStorage.setItem('meta-ai.knowledgeBaseId', this.knowledgeBaseId)
            localStorage.setItem('meta-ai.userId', this.userId)
            localStorage.setItem('meta-ai.deptIds', this.deptIds)
            localStorage.setItem('meta-ai.sessionId', this.sessionId)
        },
        /**
         * 创建新的聊天会话
         *
         * <p>
         * 只更换 sessionId
         * tenantId、knowledgeBaseId 和 userId 不变
         */
        resetSession() {
            this.sessionId = createSessionId()
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
            this.sessionId = createSessionId()
            this.contextVersion += 1
            this.persist()
        },
        /**
         * 重置顶部调试区默认值
         *
         * <p>
         * 恢复默认租户、用户、部门和知识库
         * 同时创建新会话并通知聊天页清空当前窗口
         */
        resetDefaults() {
            this.tenantId = DEFAULT_TENANT_ID
            this.knowledgeBaseId = DEFAULT_KNOWLEDGE_BASE_ID
            this.userId = DEFAULT_USER_ID
            this.deptIds = DEFAULT_DEPT_IDS
            this.sessionId = createSessionId()
            this.contextVersion += 1
            this.persist()
        },
        /**
         * 选择已有聊天会话
         *
         * <p>
         * conversationId 格式保持 tenantId:userId:sessionId
         * 点击左侧历史会话时同步工作区上下文
         */
        selectConversation(conversationId: string) {
            const [tenantId, userId, sessionId] = conversationId.split(':')
            if (tenantId) this.tenantId = tenantId
            if (userId) this.userId = userId
            if (sessionId) this.sessionId = sessionId
            this.persist()
        },
    },
})
