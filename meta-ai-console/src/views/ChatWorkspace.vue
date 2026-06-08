<template>
  <div class="chat-page">
    <aside class="history-panel surface">
      <div class="history-controls">
        <n-button class="history-new-button" size="small" secondary @click="handleNewSession">
          新建对话
        </n-button>
      </div>
      <div class="panel-head">
        <div>
          <strong>历史对话</strong>
          <span>{{ activeChat?.title || workspace.chatId }}</span>
        </div>
        <n-button class="history-refresh-button" size="tiny" tertiary :loading="chatListLoading" @click="loadChats">
          <template #icon>
            <n-icon>
              <RefreshCw/>
            </n-icon>
          </template>
          刷新
        </n-button>
      </div>
      <div class="history-list">
        <div
            v-for="item in chats"
            :key="item.id"
            :class="['history-item', { active: item.id === activeChatId, pinned: item.pinned }]"
            role="button"
            tabindex="0"
            @click="selectChat(item)"
            @keydown.enter.prevent="selectChat(item)"
            @keydown.space.prevent="selectChat(item)"
        >
          <span :class="['history-mode', modeClass(item.chatMode)]">{{ formatChatMode(item.chatMode) }}</span>
          <strong>{{ item.title }}</strong>
          <p>{{ item.lastMessage || '暂无消息' }}</p>
          <div class="history-meta">
            <time>{{ formatTime(item.lastMessageAt) }}</time>
            <div class="history-actions" @click.stop>
              <button
                  type="button"
                  :class="['history-action-button', { 'is-active': item.pinned }]"
                  :aria-pressed="item.pinned"
                  :title="item.pinned ? '取消置顶' : '置顶'"
                  @click="togglePinned(item)"
              >
                <n-icon>
                  <Pin/>
                </n-icon>
              </button>
              <button
                  type="button"
                  :class="['history-action-button', { 'is-active': item.favorite }]"
                  :aria-pressed="item.favorite"
                  :title="item.favorite ? '取消收藏' : '收藏'"
                  @click="toggleFavorite(item)"
              >
                <n-icon>
                  <Star/>
                </n-icon>
              </button>
              <button class="history-action-button" type="button" title="重命名" @click="renameSelectedChat(item)">
                <n-icon>
                  <Pencil/>
                </n-icon>
              </button>
              <button class="history-action-button" type="button" title="删除" @click="deleteSelectedChat(item)">
                <n-icon>
                  <Trash2/>
                </n-icon>
              </button>
            </div>
          </div>
        </div>
      </div>
    </aside>

    <main class="chat-surface surface">
      <div class="chat-toolbar">
        <n-radio-group
            v-model:value="chatMode"
            size="small"
        >
          <n-radio-button
              v-for="option in modeOptions"
              :key="option.value"
              :value="option.value"
          >
            {{ option.label }}
          </n-radio-button>
        </n-radio-group>
        <div class="chat-actions">
          <n-button class="mobile-new-session" size="small" tertiary @click="handleNewSession">
            <template #icon>
              <n-icon>
                <SquarePen/>
              </n-icon>
            </template>
            新建对话
          </n-button>
          <n-button size="small" tertiary @click="openRetrievalScope">
            <template #icon>
              <n-icon>
                <ListFilter/>
              </n-icon>
            </template>
            检索范围
          </n-button>
          <n-button size="small" tertiary @click="clearMessages">
            <template #icon>
              <n-icon>
                <Eraser/>
              </n-icon>
            </template>
            清屏
          </n-button>
        </div>
      </div>

      <div class="message-scroll-shell" @mouseenter="revealMessageScrollbar">
        <section
            ref="messageViewport"
            :class="['message-viewport', { transitioning: messageTransitioning }]"
            @scroll="handleMessageScroll"
        >
          <div v-if="historyLoading" class="history-loading">加载对话中...</div>
          <div v-if="messages.length === 0" class="empty-state">
            <div class="empty-mark">
              <n-icon>
                <Bot/>
              </n-icon>
            </div>
            <h2>MetaAI 知识工作台</h2>
            <p>选择普通聊天或知识问答，回答会保存到当前对话</p>
          </div>

          <article
              v-for="messageItem in messages"
              :key="messageItem.id"
              :class="['message-row', messageItem.role]"
          >
            <div v-if="messageItem.role === 'user'" class="message-actions">
              <button
                  class="message-action-button"
                  type="button"
                  title="复制消息"
                  aria-label="复制消息"
                  :disabled="!messageItem.content"
                  @click.stop="copyMessage(messageItem)"
              >
                <n-icon>
                  <Copy/>
                </n-icon>
              </button>
              <button
                  class="message-action-button"
                  type="button"
                  title="重新发送"
                  aria-label="重新发送"
                  :disabled="sending || !messageItem.content"
                  @click.stop="resendUserMessage(messageItem)"
              >
                <n-icon>
                  <RotateCcw/>
                </n-icon>
              </button>
            </div>
            <div class="message-bubble" @click="handleMessageBubbleClick">
              <div class="message-meta">
                <span :class="['message-avatar', messageItem.role]">
                  <n-icon>
                    <CircleUserRound v-if="messageItem.role === 'user'"/>
                    <Sparkles v-else/>
                  </n-icon>
                </span>
                <time>{{ messageItem.time }}</time>
              </div>
              <div
                  v-if="messageItem.role === 'assistant' && messageItem.content"
                  class="message-content markdown-body"
                  v-html="renderMarkdown(messageItem.content, messageItem.typing)"
              />
              <div v-else
                   :class="['message-content', { typing: messageItem.typing, waiting: messageItem.typing && !messageItem.content }]">
                {{ messageItem.content || (messageItem.typing ? '思考中...' : '') }}
              </div>

              <div v-if="messageItem.references?.length" class="references">
                <div class="section-label">引用来源</div>
                <div class="citation-list">
                  <button
                      v-for="reference in messageItem.references"
                      :key="reference.documentId"
                      class="citation-item"
                      type="button"
                      @click="downloadReference(reference)"
                  >
                    {{ reference.filename }}
                  </button>
                </div>
              </div>

              <div v-if="messageItem.trace" class="trace-block">
                <div class="section-label">检索 Trace</div>
                <n-code
                    :code="JSON.stringify(messageItem.trace, null, 2)"
                    language="json"
                    word-wrap
                />
              </div>
            </div>
            <div v-if="messageItem.role === 'assistant'" class="message-actions">
              <button
                  class="message-action-button"
                  type="button"
                  title="复制消息"
                  aria-label="复制消息"
                  :disabled="!messageItem.content"
                  @click.stop="copyMessage(messageItem)"
              >
                <n-icon>
                  <Copy/>
                </n-icon>
              </button>
            </div>
          </article>
        </section>

        <div
            v-if="messageScrollbarVisible"
            :class="['message-scrollbar', { active: messageScrollbarActive }]"
            aria-hidden="true"
            @mouseenter="revealMessageScrollbar"
        >
          <button
              class="message-scrollbar-button"
              type="button"
              tabindex="-1"
              @mousedown.prevent="startArrowScroll(-1)"
              @mouseup="stopArrowScroll"
              @mouseleave="stopArrowScroll"
          >
            <span class="message-scrollbar-arrow up"></span>
          </button>
          <div ref="messageScrollbarTrack" class="message-scrollbar-track" @mousedown.prevent="handleTrackMouseDown">
            <button
                class="message-scrollbar-thumb"
                type="button"
                tabindex="-1"
                :style="{ height: `${messageScrollbarThumbHeight}px`, transform: `translateY(${messageScrollbarThumbTop}px)` }"
                @mousedown.prevent.stop="startThumbDrag"
            ></button>
          </div>
          <button
              class="message-scrollbar-button"
              type="button"
              tabindex="-1"
              @mousedown.prevent="startArrowScroll(1)"
              @mouseup="stopArrowScroll"
              @mouseleave="stopArrowScroll"
          >
            <span class="message-scrollbar-arrow down"></span>
          </button>
        </div>
      </div>

      <footer class="composer">
        <n-input
            class="composer-input"
            v-model:value="draft"
            type="textarea"
            placeholder="输入问题，Enter 发送，Shift + Enter 换行"
            :autosize="{ minRows: 2, maxRows: 5 }"
            @keydown.enter="handleEnter"
        />
        <n-button
            v-if="sending"
            class="composer-button streaming-stop-button"
            type="warning"
            secondary
            @click="stopStreaming"
        >
          <template #icon>
            <span class="stop-indicator" aria-hidden="true"></span>
          </template>
          停止
        </n-button>
        <n-button
            v-else
            class="composer-button"
            type="primary"
            :disabled="!draft.trim()"
            @click="sendMessage"
        >
          <template #icon>
            <n-icon>
              <Send/>
            </n-icon>
          </template>
          发送
        </n-button>
      </footer>
    </main>

    <n-drawer v-model:show="advancedVisible" :width="420">
      <n-drawer-content title="检索范围">
        <n-form label-placement="top">
          <n-form-item label="指定文件">
            <n-select
                v-model:value="ragForm.documentId"
                :options="scopeDocumentOptions"
                :loading="scopeDocumentsLoading"
                clearable
                filterable
                placeholder="全部文件，不限定"
                @focus="loadScopeDocuments"
            />
          </n-form-item>
          <n-form-item label="文档类型">
            <n-select
                v-model:value="ragForm.documentType"
                :options="documentTypeOptions"
                clearable
                placeholder="全部类型，不限定"
            />
          </n-form-item>
        </n-form>
        <template #footer>
          <div class="scope-drawer-actions">
            <n-button tertiary @click="clearRetrievalScope">
              清除范围
            </n-button>
          </div>
        </template>
      </n-drawer-content>
    </n-drawer>

    <n-modal
        v-model:show="renameModalVisible"
        preset="card"
        title="重命名对话"
        :style="{ width: '420px', maxWidth: 'calc(100vw - 32px)' }"
        :bordered="false"
        :segmented="{ content: true, action: true }"
        @after-leave="resetRenameForm"
    >
      <n-form label-placement="top" @submit.prevent="submitRename">
        <n-form-item label="对话标题">
          <n-input
              ref="renameInputRef"
              v-model:value="renameForm.title"
              maxlength="80"
              show-count
              placeholder="输入新的对话标题"
              @keydown.enter.prevent="submitRename"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <div class="rename-modal-actions">
          <n-button tertiary :disabled="renameSubmitting" @click="renameModalVisible = false">
            取消
          </n-button>
          <n-button type="primary" :loading="renameSubmitting" :disabled="!renameForm.title.trim()"
                    @click="submitRename">
            保存
          </n-button>
        </div>
      </template>
    </n-modal>

    <n-modal
        v-model:show="deleteModalVisible"
        preset="dialog"
        type="warning"
        title="删除对话"
        positive-text="删除"
        negative-text="取消"
        :style="{ width: '380px', maxWidth: 'calc(100vw - 32px)' }"
        :positive-button-props="{ loading: deleteSubmitting, type: 'error' }"
        :negative-button-props="{ disabled: deleteSubmitting }"
        @positive-click="submitDelete"
        @negative-click="deleteModalVisible = false"
        @after-leave="resetDeleteForm"
    >
      <div class="delete-dialog-content">
        <p>删除后该对话会从历史列表中移除</p>
        <strong>{{ deleteForm.chat?.title }}</strong>
      </div>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import DOMPurify from 'dompurify'
import MarkdownIt from 'markdown-it'
import {computed, nextTick, onMounted, onUnmounted, reactive, ref, watch} from 'vue'
import {useMessage} from 'naive-ui'
import {
  Bot,
  CircleUserRound,
  Copy,
  Eraser,
  ListFilter,
  Pencil,
  Pin,
  RefreshCw,
  RotateCcw,
  Send,
  Sparkles,
  SquarePen,
  Star,
  Trash2
} from 'lucide-vue-next'

import {
  deleteChat,
  fetchChatHistoryByChatId,
  fetchChats,
  renameChat,
  streamPlainChat,
  streamRagChat,
  updateChatFlags,
} from '@/api/chat'
import {fetchStorageDocuments} from '@/api/storage'
import {useWorkspaceStore} from '@/stores/workspace'
import type {MetaChat, RetrievalCitation, RetrievalTrace, StorageDocument} from '@/types/api'

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  time: string
  typing?: boolean
  references?: RetrievalCitation[]
  trace?: RetrievalTrace
}

interface TypingRenderer {
  enqueue: (text: string) => void
  complete: (answer?: string) => void
  stop: () => void
  flush: () => void
  isDone: () => boolean
}

const workspace = useWorkspaceStore()
const message = useMessage()
const messageViewport = ref<HTMLElement | null>(null)
const messageScrollbarTrack = ref<HTMLElement | null>(null)
const messages = ref<ChatMessage[]>([])
const chats = ref<MetaChat[]>([])
const activeChatId = ref<string | null>(null)
const draft = ref('')
const sending = ref(false)
const historyLoading = ref(false)
const messageTransitioning = ref(false)
const messageScrollbarVisible = ref(false)
const messageScrollbarActive = ref(false)
const messageScrollbarThumbHeight = ref(36)
const messageScrollbarThumbTop = ref(0)
const shouldFollowLatest = ref(true)
const chatListLoading = ref(false)
const advancedVisible = ref(false)
const scopeDocumentsLoading = ref(false)
const scopeDocuments = ref<StorageDocument[]>([])
const chatMode = ref<'plain' | 'rag'>('rag')
const renameModalVisible = ref(false)
const renameSubmitting = ref(false)
const renameInputRef = ref<{ focus: () => void } | null>(null)
const renameForm = reactive({
  chat: null as MetaChat | null,
  title: '',
})
const deleteModalVisible = ref(false)
const deleteSubmitting = ref(false)
const deleteForm = reactive({
  chat: null as MetaChat | null,
})

const modeOptions = [
  {label: '知识问答', value: 'rag'},
  {label: '普通聊天', value: 'plain'},
]

const documentTypeOptions = [
  {label: 'Markdown', value: 'markdown'},
  {label: 'PDF', value: 'pdf'},
  {label: '文本', value: 'txt'},
  {label: 'JSON', value: 'json'},
  {label: 'Word', value: 'docx'},
  {label: 'Excel', value: 'xlsx'},
  {label: 'CSV', value: 'csv'},
  {label: '通用解析', value: 'tika'},
]

const scopeDocumentOptions = computed(() => {
  if (!scopeDocuments.value.length) {
    return [
      {
        label: '当前知识库暂无已索引文件',
        value: '__empty__',
        disabled: true,
      },
    ]
  }
  return scopeDocuments.value.map((item) => ({
    label: scopeDocumentLabel(item),
    value: item.documentId,
  }))
})

const TYPE_STREAM_INTERVAL_MS = 34
const TYPE_CATCHUP_INTERVAL_MS = 20
const TYPE_DONE_MAX_ANIMATION_MS = 1200
const TYPE_LARGE_BACKLOG_THRESHOLD = 1800
const TYPE_FLUSH_BACKLOG_THRESHOLD = 4000
const TYPE_SCROLL_INTERVAL_MS = 180
const MESSAGE_ARROW_SCROLL_STEP = 160
const MESSAGE_PAGE_SCROLL_RATIO = 0.5
const MESSAGE_BOTTOM_THRESHOLD = 96
const MESSAGE_SCROLLBAR_MIN_THUMB_HEIGHT = 38
const MESSAGE_SCROLLBAR_HIDE_DELAY_MS = 1200
const MESSAGE_ARROW_REPEAT_DELAY_MS = 240
const MESSAGE_ARROW_REPEAT_INTERVAL_MS = 80

const markdown = new MarkdownIt({
  breaks: true,
  html: false,
  linkify: true,
  typographer: true,
})
const defaultLinkOpenRenderer = markdown.renderer.rules.link_open
const defaultFenceRenderer = markdown.renderer.rules.fence
markdown.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  const targetIndex = token.attrIndex('target')
  const relIndex = token.attrIndex('rel')
  if (targetIndex < 0) {
    token.attrPush(['target', '_blank'])
  } else {
    token.attrs![targetIndex][1] = '_blank'
  }
  if (relIndex < 0) {
    token.attrPush(['rel', 'noopener noreferrer'])
  } else {
    token.attrs![relIndex][1] = 'noopener noreferrer'
  }
  return defaultLinkOpenRenderer ? defaultLinkOpenRenderer(tokens, idx, options, env, self) : self.renderToken(tokens, idx, options)
}
markdown.renderer.rules.fence = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  const language = token.info.trim().split(/\s+/)[0] || 'code'
  const renderedCode = defaultFenceRenderer
      ? defaultFenceRenderer(tokens, idx, options, env, self)
      : `<pre><code>${markdown.utils.escapeHtml(token.content)}</code></pre>`
  return `<div class="code-block"><div class="code-block-head"><span>${markdown.utils.escapeHtml(language)}</span><button type="button" data-copy-code>复制代码</button></div>${renderedCode}</div>`
}

const ragForm = reactive({
  documentId: null as string | null,
  documentType: null as string | null,
})

const activeChat = computed(() => chats.value.find((item) => item.id === activeChatId.value) || null)
let stopActiveTyping: (() => void) | null = null
let activeTypingRenderer: TypingRenderer | null = null
let activeStream: EventSource | null = null
let streamStoppedByUser = false
let messageResizeObserver: ResizeObserver | null = null
let messageScrollbarHideTimer: number | null = null
let arrowScrollDelayTimer: number | null = null
let arrowScrollIntervalTimer: number | null = null
let draggingThumb = false
let thumbDragStartY = 0
let thumbDragStartTop = 0

onMounted(async () => {
  await nextTick()
  setupMessageScrollbar()
  await loadChats()
  if (activeChatId.value && messages.value.length === 0) {
    await loadHistory()
  }
})

onUnmounted(() => {
  stopStreaming()
  teardownMessageScrollbar()
})

watch(() => workspace.contextVersion, async () => {
  stopStreaming()
  activeChatId.value = null
  messages.value = []
  scopeDocuments.value = []
  clearRetrievalScope()
  await loadChats()
})

watch(messages, async () => {
  await nextTick()
  updateMessageScrollbar()
}, {deep: true})

/**
 * 发送消息
 *
 * <p>
 * 普通聊天调用 /v1/chat/stream
 * RAG 聊天调用 /v1/rag/stream 并展示文件引用
 */
async function sendMessage() {
  const content = draft.value.trim()
  if (content) {
    draft.value = ''
  }
  await sendMessageContent(content)
}

async function sendMessageContent(content: string) {
  if (!content || sending.value) return

  workspace.persist()
  stopStreaming()
  messages.value.push(createMessage('user', content))
  const assistantMessage = reactive(createMessage('assistant', ''))
  assistantMessage.typing = true
  messages.value.push(assistantMessage)
  await scrollToLatest()

  sending.value = true
  streamStoppedByUser = false
  const finishSend = async () => {
    if (streamStoppedByUser) return
    activeStream = null
    sending.value = false
    await followLatestIfNeeded()
    await loadChats()
  }
  const failSend = (errorMessage: string) => {
    if (streamStoppedByUser) return
    activeStream = null
    sending.value = false
    if (!assistantMessage.content) {
      messages.value = messages.value.filter((item) => item.id !== assistantMessage.id)
    }
    message.error(errorMessage)
  }
  const typingRenderer = createTypingRenderer(assistantMessage, finishSend)
  activeTypingRenderer = typingRenderer
  stopActiveTyping = typingRenderer.stop

  const handlers = {
    onMeta: (payload: { chatId: string }) => {
      if (streamStoppedByUser) return
      workspace.selectChat(payload.chatId)
    },
    onDelta: (payload: { content: string }) => {
      if (streamStoppedByUser) return
      typingRenderer.enqueue(payload.content || '')
    },
    onDone: (payload: { answer?: string, references?: RetrievalCitation[] }) => {
      if (streamStoppedByUser) return
      assistantMessage.references = payload.references
      typingRenderer.complete(payload.answer)
    },
    onError: (payload: { message?: string }) => {
      typingRenderer.stop()
      failSend(payload.message || '发送失败')
    },
    onNetworkError: () => {
      typingRenderer.stop()
      failSend('流式连接失败')
    },
  }

  if (chatMode.value === 'plain') {
    activeStream = streamPlainChat(workspace.chatId, workspace.tenantId, workspace.userId, content, handlers)
  } else {
    activeStream = streamRagChat(
        {
          chatId: workspace.chatId,
          msg: content,
          tenantId: workspace.tenantId,
          knowledgeBaseId: workspace.knowledgeBaseId,
          userId: workspace.userId,
          deptIds: workspace.deptIds,
          documentId: ragForm.documentId || undefined,
          documentType: ragForm.documentType || undefined,
        },
        handlers,
    )
  }
}

function openRetrievalScope() {
  advancedVisible.value = true
  loadScopeDocuments()
}

async function loadScopeDocuments() {
  if (scopeDocumentsLoading.value) return

  workspace.persist()
  if (!workspace.tenantId || !workspace.knowledgeBaseId) {
    scopeDocuments.value = []
    return
  }

  scopeDocumentsLoading.value = true
  try {
    const page = await fetchStorageDocuments({
      tenantId: workspace.tenantId,
      knowledgeBaseId: workspace.knowledgeBaseId,
      indexStatus: 'INDEXED',
      current: 1,
      size: 100,
    })
    scopeDocuments.value = page.records
    if (ragForm.documentId && !page.records.some((item) => item.documentId === ragForm.documentId)) {
      ragForm.documentId = null
    }
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载可选文件失败')
  } finally {
    scopeDocumentsLoading.value = false
  }
}

function scopeDocumentLabel(document: StorageDocument) {
  const type = document.documentType ? ` · ${formatDocumentType(document.documentType)}` : ''
  return `${document.originalFilename}${type}`
}

function formatDocumentType(type: string) {
  const option = documentTypeOptions.find((item) => item.value === type)
  return option?.label || type
}

function clearRetrievalScope() {
  ragForm.documentId = null
  ragForm.documentType = null
}

function stopStreaming() {
  if (activeTypingRenderer?.isDone()) {
    activeTypingRenderer.flush()
    return
  }
  streamStoppedByUser = true
  activeStream?.close()
  activeStream = null
  stopActiveTyping?.()
  activeTypingRenderer = null
  sending.value = false
}

/**
 * 加载当前 chatId 的历史记录
 *
 * <p>
 * 历史记录来自后端 MetaChatHistory
 * 加载后会同步转换成当前页面消息列表
 */
async function loadHistory() {
  const chat = activeChat.value
  if (!chat) return
  historyLoading.value = true
  messageTransitioning.value = true
  try {
    await waitForMessageTransition()
    const page = await fetchChatHistoryByChatId(chat.chatId)
    messages.value = page.records.map((item) =>
        createMessage(
            item.role.toLowerCase() === 'user' ? 'user' : 'assistant',
            item.content,
            parseReferences(item.referencesJson),
            undefined,
            item.createdAt,
        ),
    )
    await nextTick()
    await scrollToLatest()
    messageTransitioning.value = false
  } catch (error) {
    messageTransitioning.value = false
    message.error(error instanceof Error ? error.message : '加载历史失败')
  } finally {
    historyLoading.value = false
  }
}

/**
 * 新建会话
 *
 * <p>
 * 只刷新 sessionId
 * 当前租户、知识库和用户保持不变
 */
function handleNewSession() {
  stopStreaming()
  workspace.resetSession()
  activeChatId.value = null
  messages.value = []
}

async function loadChats() {
  chatListLoading.value = true
  try {
    const page = await fetchChats(workspace.tenantId, workspace.userId)
    chats.value = page.records
    sortChats()
    const current = chats.value.find((item) => item.chatId === workspace.chatId)
    if (current) {
      activeChatId.value = current.id
    }
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载对话失败')
  } finally {
    chatListLoading.value = false
  }
}

async function selectChat(chat: MetaChat) {
  stopStreaming()
  activeChatId.value = chat.id
  workspace.selectChat(chat.chatId)
  await loadHistory()
}

async function togglePinned(chat: MetaChat) {
  const nextPinned = !chat.pinned
  try {
    const updatedChat = await updateChatFlags(chat.id, {pinned: nextPinned})
    patchChatInList(updatedChat)
    sortChats()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '更新置顶失败')
  }
}

async function toggleFavorite(chat: MetaChat) {
  const nextFavorite = !chat.favorite
  try {
    const updatedChat = await updateChatFlags(chat.id, {favorite: nextFavorite})
    patchChatInList(updatedChat)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '更新收藏失败')
  }
}

function patchChatInList(updatedChat: MetaChat) {
  const index = chats.value.findIndex((item) => item.id === updatedChat.id)
  if (index >= 0) {
    chats.value[index] = updatedChat
  }
}

function sortChats() {
  chats.value = [...chats.value].sort((left, right) => {
    if (left.pinned !== right.pinned) {
      return left.pinned ? -1 : 1
    }
    return toTime(right.lastMessageAt || right.updatedAt || right.createdAt) - toTime(left.lastMessageAt || left.updatedAt || left.createdAt)
  })
}

async function renameSelectedChat(chat: MetaChat) {
  renameForm.chat = chat
  renameForm.title = chat.title
  renameModalVisible.value = true
  await nextTick()
  renameInputRef.value?.focus()
}

async function submitRename() {
  const chat = renameForm.chat
  const title = renameForm.title.trim()
  if (!chat || !title || renameSubmitting.value) return

  if (title === chat.title) {
    renameModalVisible.value = false
    return
  }

  renameSubmitting.value = true
  try {
    const updatedChat = await renameChat(chat.id, title)
    patchChatInList(updatedChat)
    renameModalVisible.value = false
  } catch (error) {
    message.error(error instanceof Error ? error.message : '重命名失败')
  } finally {
    renameSubmitting.value = false
  }
}

function resetRenameForm() {
  renameForm.chat = null
  renameForm.title = ''
}

function deleteSelectedChat(chat: MetaChat) {
  deleteForm.chat = chat
  deleteModalVisible.value = true
}

async function submitDelete() {
  const chat = deleteForm.chat
  if (!chat || deleteSubmitting.value) return false

  deleteSubmitting.value = true
  try {
    await deleteChat(chat.id)
    if (activeChatId.value === chat.id) {
      activeChatId.value = null
      messages.value = []
      workspace.resetSession()
    }
    await loadChats()
    deleteModalVisible.value = false
    return true
  } catch (error) {
    message.error(error instanceof Error ? error.message : '删除失败')
    return false
  } finally {
    deleteSubmitting.value = false
  }
}

function resetDeleteForm() {
  deleteForm.chat = null
}

function clearMessages() {
  stopStreaming()
  messages.value = []
}

async function copyText(text: string, successText: string) {
  if (!text) return
  try {
    await writeClipboardText(text)
    message.success(successText)
  } catch {
    message.error('复制失败，请检查浏览器剪贴板权限')
  }
}

async function writeClipboardText(text: string) {
  if (navigator.clipboard?.writeText) {
    try {
      await Promise.race([
        navigator.clipboard.writeText(text),
        new Promise((_, reject) => window.setTimeout(() => reject(new Error('clipboard timeout')), 1200)),
      ])
      return
    } catch {
      // 继续走 textarea fallback
    }
  }

  const textarea = window.document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', 'true')
  textarea.style.position = 'fixed'
  textarea.style.left = '-9999px'
  textarea.style.top = '0'
  window.document.body.appendChild(textarea)
  textarea.select()
  const copied = window.document.execCommand('copy')
  window.document.body.removeChild(textarea)
  if (!copied) {
    throw new Error('copy failed')
  }
}

function copyMessage(messageItem: ChatMessage) {
  copyText(messageItem.content, '已复制消息')
}

function resendUserMessage(messageItem: ChatMessage) {
  if (messageItem.role !== 'user' || sending.value || !messageItem.content) return
  sendMessageContent(messageItem.content)
}

function handleMessageBubbleClick(event: MouseEvent) {
  const target = event.target as HTMLElement | null
  const button = target?.closest<HTMLButtonElement>('[data-copy-code]')
  if (!button) return
  const codeBlock = button.closest('.code-block')
  const code = codeBlock?.querySelector('code')?.textContent || ''
  copyText(code, '已复制代码')
}

/**
 * 输入框 Enter 发送
 *
 * <p>
 * Shift + Enter 保留为换行
 */
function handleEnter(event: KeyboardEvent) {
  if (event.shiftKey) return
  event.preventDefault()
  sendMessage()
}

/**
 * 创建前端消息对象
 *
 * @example
 * createMessage('user', '请总结知识库')
 */
function createMessage(
    role: ChatMessage['role'],
    content: string,
    references?: RetrievalCitation[],
    trace?: RetrievalTrace,
    createdAt?: string,
): ChatMessage {
  return {
    id: crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`,
    role,
    content,
    references,
    trace,
    time: formatMessageTime(createdAt),
  }
}

function formatMessageTime(value?: string | Date) {
  return formatDateTime(value ? new Date(value) : new Date())
}

function setupMessageScrollbar() {
  updateMessageScrollbar()
  messageResizeObserver = new ResizeObserver(updateMessageScrollbar)
  if (messageViewport.value) {
    messageResizeObserver.observe(messageViewport.value)
  }
  window.addEventListener('resize', updateMessageScrollbar)
  window.addEventListener('mousemove', handleThumbDrag)
  window.addEventListener('mouseup', stopThumbDrag)
  window.addEventListener('mouseup', stopArrowScroll)
}

function teardownMessageScrollbar() {
  messageResizeObserver?.disconnect()
  messageResizeObserver = null
  window.removeEventListener('resize', updateMessageScrollbar)
  window.removeEventListener('mousemove', handleThumbDrag)
  window.removeEventListener('mouseup', stopThumbDrag)
  window.removeEventListener('mouseup', stopArrowScroll)
  clearMessageScrollbarHideTimer()
  stopArrowScroll()
  stopThumbDrag()
}

function handleMessageScroll() {
  updateFollowLatestState()
  updateMessageScrollbar()
  revealMessageScrollbar()
}

function updateFollowLatestState() {
  const viewport = messageViewport.value
  if (!viewport) return
  shouldFollowLatest.value = viewport.scrollHeight - viewport.scrollTop - viewport.clientHeight <= MESSAGE_BOTTOM_THRESHOLD
}

function updateMessageScrollbar() {
  const viewport = messageViewport.value
  const track = messageScrollbarTrack.value
  if (!viewport) return

  const scrollable = viewport.scrollHeight - viewport.clientHeight > 1
  messageScrollbarVisible.value = scrollable
  if (!scrollable) {
    messageScrollbarThumbTop.value = 0
    return
  }
  if (!track) {
    nextTick(updateMessageScrollbar)
  }

  const trackHeight = track?.clientHeight || Math.max(viewport.clientHeight - 60, 1)
  const thumbHeight = Math.max(
      MESSAGE_SCROLLBAR_MIN_THUMB_HEIGHT,
      Math.round((viewport.clientHeight / viewport.scrollHeight) * trackHeight),
  )
  const maxThumbTop = Math.max(trackHeight - thumbHeight, 0)
  const maxScrollTop = Math.max(viewport.scrollHeight - viewport.clientHeight, 1)

  messageScrollbarThumbHeight.value = Math.min(thumbHeight, trackHeight)
  messageScrollbarThumbTop.value = Math.round((viewport.scrollTop / maxScrollTop) * maxThumbTop)
}

function revealMessageScrollbar() {
  if (!messageScrollbarVisible.value) return
  messageScrollbarActive.value = true
  clearMessageScrollbarHideTimer()
  messageScrollbarHideTimer = window.setTimeout(() => {
    if (!draggingThumb) {
      messageScrollbarActive.value = false
    }
  }, MESSAGE_SCROLLBAR_HIDE_DELAY_MS)
}

function clearMessageScrollbarHideTimer() {
  if (messageScrollbarHideTimer !== null) {
    window.clearTimeout(messageScrollbarHideTimer)
    messageScrollbarHideTimer = null
  }
}

function scrollMessageBy(delta: number, behavior: ScrollBehavior = 'smooth') {
  const viewport = messageViewport.value
  if (!viewport) return
  viewport.scrollBy({top: delta, behavior})
}

function startArrowScroll(direction: -1 | 1) {
  stopArrowScroll()
  scrollMessageBy(direction * MESSAGE_ARROW_SCROLL_STEP)
  arrowScrollDelayTimer = window.setTimeout(() => {
    arrowScrollIntervalTimer = window.setInterval(() => {
      scrollMessageBy(direction * MESSAGE_ARROW_SCROLL_STEP, 'auto')
    }, MESSAGE_ARROW_REPEAT_INTERVAL_MS)
  }, MESSAGE_ARROW_REPEAT_DELAY_MS)
}

function stopArrowScroll() {
  if (arrowScrollDelayTimer !== null) {
    window.clearTimeout(arrowScrollDelayTimer)
    arrowScrollDelayTimer = null
  }
  if (arrowScrollIntervalTimer !== null) {
    window.clearInterval(arrowScrollIntervalTimer)
    arrowScrollIntervalTimer = null
  }
}

function handleTrackMouseDown(event: MouseEvent) {
  const viewport = messageViewport.value
  const track = messageScrollbarTrack.value
  if (!viewport || !track) return

  const rect = track.getBoundingClientRect()
  const clickedTop = event.clientY - rect.top
  if (clickedTop < messageScrollbarThumbTop.value) {
    scrollMessageBy(-Math.round(viewport.clientHeight * MESSAGE_PAGE_SCROLL_RATIO))
    return
  }
  if (clickedTop > messageScrollbarThumbTop.value + messageScrollbarThumbHeight.value) {
    scrollMessageBy(Math.round(viewport.clientHeight * MESSAGE_PAGE_SCROLL_RATIO))
  }
}

function startThumbDrag(event: MouseEvent) {
  draggingThumb = true
  thumbDragStartY = event.clientY
  thumbDragStartTop = messageScrollbarThumbTop.value
  messageScrollbarActive.value = true
  document.body.classList.add('message-scrollbar-dragging')
}

function handleThumbDrag(event: MouseEvent) {
  if (!draggingThumb) return
  const viewport = messageViewport.value
  const track = messageScrollbarTrack.value
  if (!viewport || !track) return

  const maxThumbTop = Math.max(track.clientHeight - messageScrollbarThumbHeight.value, 1)
  const nextThumbTop = clamp(thumbDragStartTop + event.clientY - thumbDragStartY, 0, maxThumbTop)
  const maxScrollTop = Math.max(viewport.scrollHeight - viewport.clientHeight, 1)
  viewport.scrollTop = (nextThumbTop / maxThumbTop) * maxScrollTop
}

function stopThumbDrag() {
  if (!draggingThumb) return
  draggingThumb = false
  document.body.classList.remove('message-scrollbar-dragging')
  revealMessageScrollbar()
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max)
}

/**
 * 滚动到最新消息
 */
async function scrollToLatest() {
  await nextTick()
  if (messageViewport.value) {
    messageViewport.value.scrollTop = messageViewport.value.scrollHeight
    shouldFollowLatest.value = true
    updateMessageScrollbar()
  }
}

async function followLatestIfNeeded() {
  if (!shouldFollowLatest.value) {
    await nextTick()
    updateMessageScrollbar()
    return
  }
  await scrollToLatest()
}

function waitForMessageTransition() {
  return new Promise((resolve) => window.setTimeout(resolve, 160))
}

function createTypingRenderer(assistantMessage: ChatMessage, onComplete: () => Promise<void>): TypingRenderer {
  let pendingText = ''
  let finalAnswer: string | undefined
  let done = false
  let completed = false
  let stopped = false
  let timer: number | null = null
  let streamDoneAt = 0
  let lastScrollAt = 0
  let renderer: TypingRenderer

  const stop = () => {
    stopped = true
    assistantMessage.typing = false
    if (timer !== null) {
      window.clearTimeout(timer)
      timer = null
    }
    if (stopActiveTyping === stop) {
      stopActiveTyping = null
    }
    if (activeTypingRenderer === renderer) {
      activeTypingRenderer = null
    }
  }

  const schedule = (delay = resolveTypingDelay(done)) => {
    if (!stopped && timer === null) {
      timer = window.setTimeout(tick, delay)
    }
  }

  const finish = () => {
    if (completed) return
    completed = true
    if (finalAnswer !== undefined && assistantMessage.content !== finalAnswer) {
      assistantMessage.content = finalAnswer
      messages.value = [...messages.value]
    }
    stop()
    followLatestIfNeeded()
    void onComplete()
  }

  const flush = () => {
    if (stopped || completed) return
    if (finalAnswer !== undefined) {
      assistantMessage.content = finalAnswer
    } else if (pendingText) {
      assistantMessage.content += pendingText
    }
    pendingText = ''
    messages.value = [...messages.value]
    finish()
  }

  const tick = () => {
    timer = null
    if (stopped) return

    if (pendingText) {
      if (shouldFlushTyping(pendingText.length, done, streamDoneAt)) {
        flush()
        return
      }
      const batchSize = resolveTypingBatchSize(pendingText.length, done, streamDoneAt)
      const chars = Array.from(pendingText)
      const nextText = chars.slice(0, batchSize).join('')
      assistantMessage.content += nextText
      pendingText = chars.slice(batchSize).join('')
      messages.value = [...messages.value]
      throttleScrollToLatest()
      schedule(resolveTypingDelay(done))
      return
    }

    if (done) {
      finish()
    }
  }

  const throttleScrollToLatest = () => {
    const now = window.performance.now()
    if (now - lastScrollAt >= TYPE_SCROLL_INTERVAL_MS) {
      lastScrollAt = now
      followLatestIfNeeded()
    }
  }

  renderer = {
    enqueue(text: string) {
      if (!text || stopped || completed) return
      pendingText += text
      schedule()
    },
    complete(answer?: string) {
      if (stopped || completed) return
      finalAnswer = answer
      if (answer) {
        const bufferedContent = assistantMessage.content + pendingText
        if (answer.startsWith(bufferedContent)) {
          pendingText += answer.slice(bufferedContent.length)
        } else {
          pendingText = answer.slice(assistantMessage.content.length)
        }
      }
      done = true
      streamDoneAt = window.performance.now()
      schedule()
    },
    flush,
    isDone() {
      return done
    },
    stop,
  }

  return renderer
}

function resolveTypingDelay(done: boolean) {
  return done ? TYPE_CATCHUP_INTERVAL_MS : TYPE_STREAM_INTERVAL_MS
}

function resolveTypingBatchSize(pendingLength: number, done: boolean, streamDoneAt: number) {
  if (!done) {
    if (pendingLength > 900) return 8
    if (pendingLength > 360) return 5
    if (pendingLength > 120) return 3
    return 1
  }

  const elapsedAfterDone = window.performance.now() - streamDoneAt
  if (pendingLength > TYPE_LARGE_BACKLOG_THRESHOLD) return 96
  if (elapsedAfterDone > 800) return 96
  if (pendingLength > 900) return 64
  if (pendingLength > 360) return 32
  if (pendingLength > 120) return 16
  return 8
}

function shouldFlushTyping(pendingLength: number, done: boolean, streamDoneAt: number) {
  if (!done) return false
  return pendingLength > TYPE_FLUSH_BACKLOG_THRESHOLD ||
      window.performance.now() - streamDoneAt > TYPE_DONE_MAX_ANIMATION_MS
}

function renderMarkdown(content: string, typing?: boolean) {
  const html = markdown.render(content)
  return DOMPurify.sanitize(typing ? appendTypingCaret(html) : html, {
    ADD_ATTR: ['target', 'data-copy-code', 'type'],
  })
}

function appendTypingCaret(html: string) {
  const caret = '<span class="typing-caret"></span>'
  const blockEndings = ['</p>', '</li>', '</code>', '</h1>', '</h2>', '</h3>', '</h4>', '</blockquote>']
  for (const ending of blockEndings) {
    const index = html.lastIndexOf(ending)
    if (index >= 0) {
      return `${html.slice(0, index)}${caret}${html.slice(index)}`
    }
  }
  return `${html}${caret}`
}

function parseReferences(value?: string): RetrievalCitation[] | undefined {
  if (!value) return undefined
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : undefined
  } catch {
    return undefined
  }
}

function formatTime(value?: string) {
  return value ? formatDateTime(new Date(value)) : ''
}

function formatDateTime(date: Date) {
  if (Number.isNaN(date.getTime())) return ''
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hours = `${date.getHours()}`.padStart(2, '0')
  const minutes = `${date.getMinutes()}`.padStart(2, '0')
  const seconds = `${date.getSeconds()}`.padStart(2, '0')
  return `${date.getFullYear()}年${month}月${day}日 ${hours}:${minutes}:${seconds}`
}

function toTime(value?: string) {
  if (!value) return 0
  const time = new Date(value).getTime()
  return Number.isNaN(time) ? 0 : time
}

function formatChatMode(value?: string) {
  const normalized = value?.toLowerCase()
  if (normalized === 'rag' || normalized === 'rag_details') {
    return '知识问答'
  }
  if (normalized === 'chat' || normalized === 'plain') {
    return '普通聊天'
  }
  return '对话'
}

function modeClass(value?: string) {
  const normalized = value?.toLowerCase()
  if (normalized === 'rag' || normalized === 'rag_details') {
    return 'knowledge'
  }
  if (normalized === 'chat' || normalized === 'plain') {
    return 'plain'
  }
  return 'default'
}

async function downloadReference(reference: RetrievalCitation) {
  try {
    const response = await fetch(`/api/v1/storage/documents/download/${encodeURIComponent(reference.documentId)}`)
    if (!response.ok) {
      throw new Error(`下载失败：${response.status}`)
    }
    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    const link = window.document.createElement('a')
    link.href = url
    link.download = reference.filename
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '下载失败')
  }
}
</script>

<style scoped>
.chat-page {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
  height: 100%;
  min-height: 0;
}

.history-panel {
  display: flex;
  min-height: 0;
  flex-direction: column;
  padding: 14px;
}

.panel-head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 11px;
}

.panel-head > div {
  min-width: 0;
}

.panel-head strong {
  display: block;
  color: #f4f7fb;
  font-size: 14px;
  font-weight: 750;
}

.panel-head span {
  display: block;
  max-width: 100%;
  margin-top: 4px;
  overflow: hidden;
  color: #7f8b9b;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-controls {
  margin-bottom: 16px;
}

.history-controls :deep(.n-button) {
  width: 100%;
  height: 38px;
  font-size: 14px;
  font-weight: 750;
}

.history-new-button {
  --n-color: rgba(65, 214, 183, 0.18) !important;
  --n-color-hover: rgba(65, 214, 183, 0.26) !important;
  --n-color-pressed: rgba(65, 214, 183, 0.2) !important;
  --n-color-focus: rgba(65, 214, 183, 0.24) !important;
  --n-border: 1px solid rgba(65, 214, 183, 0.36) !important;
  --n-border-hover: 1px solid rgba(65, 214, 183, 0.58) !important;
  --n-border-pressed: 1px solid rgba(65, 214, 183, 0.5) !important;
  --n-border-focus: 1px solid rgba(65, 214, 183, 0.58) !important;
  --n-text-color: #b9fff0 !important;
  --n-text-color-hover: #e7fff8 !important;
  --n-text-color-pressed: #d8fff6 !important;
  --n-text-color-focus: #e7fff8 !important;
  box-shadow: 0 0 0 1px rgba(65, 214, 183, 0.05), 0 10px 24px rgba(65, 214, 183, 0.08);
}

.history-refresh-button {
  flex: none;
  justify-self: end;
}

.history-list {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  overflow: auto;
  scrollbar-width: none;
}

.history-list::-webkit-scrollbar {
  display: none;
}

.history-item {
  width: 100%;
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 8px;
  padding: 10px;
  color: inherit;
  text-align: left;
  background: rgba(255, 255, 255, 0.03);
  cursor: pointer;
}

.history-item:focus-visible {
  outline: 2px solid rgba(65, 214, 183, 0.55);
  outline-offset: 2px;
}

.history-item.active {
  border-color: rgba(65, 214, 183, 0.45);
  background: rgba(65, 214, 183, 0.1);
}

.history-item.pinned {
  border-color: rgba(247, 201, 72, 0.42);
  background: linear-gradient(135deg, rgba(247, 201, 72, 0.12), rgba(255, 255, 255, 0.03));
}

.history-item.active.pinned {
  border-color: rgba(65, 214, 183, 0.5);
  background: linear-gradient(135deg, rgba(247, 201, 72, 0.14), rgba(65, 214, 183, 0.12));
}

.history-mode {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 999px;
  padding: 2px 7px;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.4;
}

.history-mode.knowledge {
  border-color: rgba(65, 214, 183, 0.24);
  color: #76ead2;
  background: rgba(65, 214, 183, 0.1);
}

.history-mode.plain {
  border-color: rgba(126, 168, 255, 0.22);
  color: #a8c4ff;
  background: rgba(126, 168, 255, 0.08);
}

.history-mode.default {
  color: #aeb9c8;
  background: rgba(255, 255, 255, 0.05);
}

.history-item strong {
  display: block;
  margin-top: 5px;
  overflow: hidden;
  color: #f4f7fb;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-item p {
  display: -webkit-box;
  margin: 5px 0 0;
  overflow: hidden;
  color: #c5cfdd;
  font-size: 12px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.history-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 9px;
  color: #7f8b9b;
  font-size: 11px;
}

.history-actions {
  display: flex;
  flex: none;
  gap: 4px;
}

.history-action-button {
  display: grid;
  width: 24px;
  height: 24px;
  place-items: center;
  border: 0;
  border-radius: 5px;
  padding: 0;
  color: #9aa8ba;
  line-height: 1;
  background: rgba(255, 255, 255, 0.05);
  cursor: pointer;
}

.history-action-button :deep(.n-icon) {
  display: grid;
  width: 16px;
  height: 16px;
  place-items: center;
  line-height: 1;
}

.history-action-button :deep(svg) {
  display: block;
  width: 16px;
  height: 16px;
}

.history-action-button:hover {
  color: #41d6b7;
  background: rgba(65, 214, 183, 0.12);
}

.history-action-button.is-active {
  color: #f7c948;
  background: rgba(247, 201, 72, 0.16);
}

.history-action-button.is-active:hover {
  color: #ffe082;
  background: rgba(247, 201, 72, 0.22);
}

.history-action-button:focus-visible {
  outline: 2px solid rgba(247, 201, 72, 0.5);
  outline-offset: 2px;
}

.rename-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.delete-dialog-content {
  display: grid;
  gap: 8px;
}

.delete-dialog-content p {
  margin: 0;
  color: #aeb9c8;
  font-size: 13px;
}

.delete-dialog-content strong {
  overflow: hidden;
  color: #f4f7fb;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scope-drawer-actions {
  display: flex;
  justify-content: flex-end;
}

.chat-surface {
  display: grid;
  min-width: 0;
  min-height: 0;
  grid-template-rows: auto minmax(0, 1fr) auto;
  overflow: hidden;
}

.chat-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.chat-actions {
  display: flex;
  gap: 8px;
}

.mobile-new-session {
  display: none;
}

.message-scroll-shell {
  position: relative;
  min-height: 0;
  overflow: hidden;
}

.message-viewport {
  position: relative;
  height: 100%;
  min-height: 0;
  padding: 20px 34px 20px 20px;
  overflow: auto;
  scrollbar-width: none;
  opacity: 1;
  transition: opacity 120ms ease;
}

.message-viewport::-webkit-scrollbar {
  display: none;
}

.message-viewport.transitioning {
  opacity: 0.55;
}

.message-scrollbar {
  position: absolute;
  top: 18px;
  right: 10px;
  bottom: 18px;
  z-index: 4;
  display: grid;
  width: 18px;
  grid-template-rows: 18px minmax(0, 1fr) 18px;
  gap: 0;
  border: 0;
  border-radius: 999px;
  padding: 2px 0;
  opacity: 0.7;
  background: rgba(244, 248, 252, 0.78);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.18);
  transition: opacity 160ms ease;
  pointer-events: auto;
}

.message-scroll-shell:hover .message-scrollbar,
.message-scrollbar.active {
  opacity: 1;
}

.message-scrollbar-button {
  display: grid;
  width: 18px;
  height: 18px;
  place-items: center;
  border: 0;
  border-radius: 999px;
  padding: 0;
  background: transparent;
  cursor: pointer;
}

.message-scrollbar-arrow {
  display: block;
  width: 9px;
  height: 7px;
  background: #8f969d;
}

.message-scrollbar-arrow.up {
  clip-path: polygon(50% 8%, 94% 70%, 78% 90%, 50% 52%, 22% 90%, 6% 70%);
}

.message-scrollbar-arrow.down {
  clip-path: polygon(6% 30%, 22% 10%, 50% 48%, 78% 10%, 94% 30%, 50% 92%);
}

.message-scrollbar-button:hover .message-scrollbar-arrow.up {
  background: #7e8790;
}

.message-scrollbar-button:hover .message-scrollbar-arrow.down {
  background: #7e8790;
}

.message-scrollbar-track {
  position: relative;
  justify-self: center;
  width: 12px;
  min-height: 0;
  border-radius: 999px;
  background: transparent;
  box-shadow: none;
  cursor: pointer;
}

.message-scrollbar-thumb {
  position: absolute;
  top: 0;
  left: 2px;
  width: 8px;
  border: 0;
  border-radius: 999px;
  padding: 0;
  background: #8f969d;
  box-shadow: none;
  cursor: grab;
}

.message-scrollbar-thumb:hover,
.message-scrollbar-thumb:active {
  background: #7e8790;
}

.message-scrollbar-thumb:active {
  cursor: grabbing;
}

:global(.message-scrollbar-dragging) {
  user-select: none;
}

.history-loading {
  position: absolute;
  top: 50%;
  left: 50%;
  z-index: 2;
  width: fit-content;
  margin: 0;
  border: 1px solid rgba(65, 214, 183, 0.14);
  border-radius: 999px;
  padding: 5px 10px;
  color: #9adccc;
  font-size: 12px;
  background: rgba(13, 17, 23, 0.76);
  backdrop-filter: blur(8px);
  pointer-events: none;
  transform: translate(-50%, -50%);
}

.empty-state {
  display: grid;
  height: 100%;
  min-height: 360px;
  place-items: center;
  align-content: center;
  color: #9aa8ba;
  text-align: center;
}

.empty-mark {
  display: grid;
  width: 58px;
  height: 58px;
  place-items: center;
  border: 1px solid rgba(65, 214, 183, 0.34);
  border-radius: 14px;
  color: #41d6b7;
  font-size: 30px;
  background: rgba(65, 214, 183, 0.08);
}

.empty-state h2 {
  margin: 16px 0 8px;
  color: #f4f7fb;
  font-size: 22px;
}

.empty-state p {
  margin: 0;
}

.message-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  margin-bottom: 16px;
}

.message-row.user {
  justify-content: flex-end;
}

.message-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 9px;
  color: #7f8b9b;
  font-size: 12px;
}

.message-avatar {
  display: grid;
  width: 24px;
  height: 24px;
  flex: none;
  place-items: center;
  border-radius: 7px;
  font-size: 15px;
}

.message-avatar.assistant {
  border: 1px solid rgba(65, 214, 183, 0.28);
  color: #76ead2;
  background: radial-gradient(circle at 35% 30%, rgba(65, 214, 183, 0.22), transparent 56%),
  rgba(12, 19, 25, 0.92);
  box-shadow: 0 0 22px rgba(65, 214, 183, 0.08);
}

.message-avatar.user {
  border: 1px solid rgba(126, 168, 255, 0.24);
  color: #c2d2ff;
  background: radial-gradient(circle at 35% 30%, rgba(126, 168, 255, 0.18), transparent 58%),
  rgba(15, 22, 32, 0.92);
}

.message-bubble {
  max-width: min(780px, 78%);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 13px 14px;
  background: rgba(255, 255, 255, 0.045);
}

.message-row.user .message-bubble {
  border-color: rgba(65, 214, 183, 0.26);
  background: rgba(65, 214, 183, 0.09);
}

.message-meta time {
  flex: none;
  white-space: nowrap;
}

.message-content {
  white-space: pre-wrap;
  word-break: break-word;
  color: #dce5f0;
  line-height: 1.7;
}

.message-actions {
  display: flex;
  width: 58px;
  flex: none;
  justify-content: flex-start;
  gap: 6px;
  opacity: 0;
  pointer-events: none;
  transform: translateY(-2px);
  transition: opacity 140ms ease, transform 140ms ease;
}

.message-row.user .message-actions {
  justify-content: flex-start;
}

.message-row.assistant .message-actions {
  width: 26px;
  justify-content: flex-end;
}

.message-row:hover .message-actions,
.message-row:focus-within .message-actions {
  opacity: 1;
  pointer-events: auto;
  transform: translateY(0);
}

.message-action-button {
  display: grid;
  width: 26px;
  height: 26px;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 6px;
  padding: 0;
  color: #9aa8ba;
  line-height: 1;
  background: rgba(255, 255, 255, 0.055);
  cursor: pointer;
}

.message-action-button:hover:not(:disabled) {
  border-color: rgba(65, 214, 183, 0.32);
  color: #79ead5;
  background: rgba(65, 214, 183, 0.1);
}

.message-action-button:disabled {
  opacity: 0.42;
  cursor: not-allowed;
}

.message-action-button :deep(.n-icon) {
  display: grid;
  width: 15px;
  height: 15px;
  place-items: center;
}

.message-action-button :deep(svg) {
  display: block;
  width: 15px;
  height: 15px;
}

.message-content.typing:not(.markdown-body)::after,
.markdown-body :deep(.typing-caret) {
  content: '';
  display: inline-block;
  width: 1px;
  height: 1em;
  margin-left: 3px;
  vertical-align: -0.12em;
  background: rgba(65, 214, 183, 0.68);
  animation: caret-blink 1.2s ease-in-out infinite;
}

.message-content.waiting {
  color: #9aa8ba;
}

.markdown-body {
  white-space: normal;
}

.markdown-body :deep(p) {
  margin: 0 0 10px;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 14px 0 8px;
  color: #f4f7fb;
  line-height: 1.35;
}

.markdown-body :deep(h1:first-child),
.markdown-body :deep(h2:first-child),
.markdown-body :deep(h3:first-child),
.markdown-body :deep(h4:first-child) {
  margin-top: 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 8px 0 10px;
  padding-left: 22px;
}

.markdown-body :deep(li) {
  margin: 4px 0;
}

.markdown-body :deep(blockquote) {
  margin: 10px 0;
  border-left: 3px solid rgba(65, 214, 183, 0.55);
  padding: 6px 0 6px 12px;
  color: #b7c5d6;
  background: rgba(65, 214, 183, 0.06);
}

.markdown-body :deep(a) {
  color: #69e6cb;
  text-decoration: none;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

.markdown-body :deep(code) {
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 4px;
  padding: 1px 5px;
  color: #d8f7ee;
  font-family: Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 0.92em;
  background: rgba(0, 0, 0, 0.28);
}

.markdown-body :deep(.code-block) {
  margin: 10px 0;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  overflow: hidden;
  background: rgba(3, 8, 14, 0.72);
}

.markdown-body :deep(.code-block-head) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  padding: 7px 10px;
  color: #8fa0b3;
  font-size: 12px;
  background: rgba(255, 255, 255, 0.035);
}

.markdown-body :deep(.code-block-head span) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.markdown-body :deep(.code-block-head button) {
  flex: none;
  border: 0;
  border-radius: 5px;
  padding: 3px 7px;
  color: #aeeede;
  font: inherit;
  font-size: 12px;
  background: rgba(65, 214, 183, 0.1);
  cursor: pointer;
}

.markdown-body :deep(.code-block-head button:hover) {
  color: #e8fff8;
  background: rgba(65, 214, 183, 0.18);
}

.markdown-body :deep(pre) {
  margin: 0;
  padding: 12px;
  overflow: auto;
  background: transparent;
}

.markdown-body :deep(pre code) {
  display: block;
  border: 0;
  padding: 0;
  color: #e8eef6;
  font-size: 13px;
  line-height: 1.65;
  white-space: pre;
  background: transparent;
}

.markdown-body :deep(table) {
  display: block;
  width: 100%;
  margin: 10px 0;
  overflow-x: auto;
  border-collapse: collapse;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 7px 9px;
  text-align: left;
}

.markdown-body :deep(th) {
  color: #f4f7fb;
  background: rgba(255, 255, 255, 0.06);
}

@keyframes caret-blink {
  50% {
    opacity: 0;
  }
}

.references,
.trace-block {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.section-label {
  margin-bottom: 8px;
  color: #41d6b7;
  font-size: 12px;
  font-weight: 800;
}

.citation-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.citation-item {
  max-width: 100%;
  border: 1px solid rgba(65, 214, 183, 0.26);
  border-radius: 6px;
  padding: 5px 9px;
  overflow: hidden;
  color: #b7f3e5;
  font: inherit;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: rgba(65, 214, 183, 0.08);
  cursor: pointer;
}

.composer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.composer-input :deep(.n-input__textarea-el::placeholder) {
  color: rgba(154, 168, 186, 0.82);
}

.composer-button {
  min-width: 92px;
  height: 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.streaming-stop-button {
  position: relative;
  overflow: hidden;
  --stop-pulse-duration: 1.8s;
  --n-color: rgba(82, 60, 27, 0.82) !important;
  --n-color-hover: rgba(95, 69, 30, 0.9) !important;
  --n-color-pressed: rgba(76, 54, 24, 0.92) !important;
  --n-border: 1px solid rgba(255, 203, 104, 0.34) !important;
  --n-border-hover: 1px solid rgba(255, 215, 128, 0.58) !important;
  --n-border-pressed: 1px solid rgba(255, 203, 104, 0.5) !important;
  --n-text-color: #ffe3a3 !important;
  --n-text-color-hover: #fff0c7 !important;
  --n-text-color-pressed: #ffe7ad !important;
  box-shadow: 0 0 0 1px rgba(255, 203, 104, 0.08), 0 10px 22px rgba(255, 173, 52, 0.08);
  animation: stop-button-pulse var(--stop-pulse-duration) ease-in-out infinite;
}

.streaming-stop-button::before {
  position: absolute;
  right: auto;
  bottom: 0;
  left: -46%;
  width: 46%;
  height: 2px;
  border-radius: 999px;
  background: linear-gradient(90deg, transparent, rgba(255, 221, 143, 0.95), transparent);
  content: '';
  pointer-events: none;
  animation: stop-scan-line 1.45s ease-in-out infinite;
}

.streaming-stop-button::after {
  position: absolute;
  inset: 3px;
  border: 1px solid rgba(255, 210, 118, 0.18);
  border-radius: inherit;
  content: '';
  pointer-events: none;
}

.stop-indicator {
  position: relative;
  display: inline-grid;
  width: 21px;
  height: 21px;
  place-items: center;
}

.stop-indicator::after {
  width: 12px;
  height: 12px;
  border-radius: 3px;
  background: #ffd985;
  box-shadow: 0 0 0 1px rgba(255, 236, 187, 0.28), 0 0 8px rgba(255, 189, 76, 0.18);
  content: '';
  animation: stop-square-pulse var(--stop-pulse-duration) ease-in-out infinite;
}

@keyframes stop-button-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 1px rgba(255, 203, 104, 0.08), 0 10px 22px rgba(255, 173, 52, 0.08);
  }

  50% {
    box-shadow: 0 0 0 1px rgba(255, 203, 104, 0.18), 0 10px 26px rgba(255, 173, 52, 0.2);
  }
}

@keyframes stop-scan-line {
  0% {
    left: -46%;
  }

  100% {
    left: 100%;
  }
}

@keyframes stop-square-pulse {
  0%,
  100% {
    background: #ffd177;
    box-shadow: 0 0 0 1px rgba(255, 236, 187, 0.22), 0 0 5px rgba(255, 189, 76, 0.12);
  }

  50% {
    background: #ffe8aa;
    box-shadow: 0 0 0 1px rgba(255, 244, 211, 0.44), 0 0 12px rgba(255, 196, 85, 0.32);
  }
}

@media (prefers-reduced-motion: reduce) {
  .message-scrollbar {
    transition: none;
  }

  .streaming-stop-button,
  .streaming-stop-button::before,
  .stop-indicator::after {
    animation: none;
  }
}

@media (max-width: 1100px) {
  .chat-page {
    grid-template-columns: 1fr;
  }

  .history-panel {
    display: none;
  }

  .mobile-new-session {
    display: inline-flex;
  }

  .message-viewport {
    padding-right: 20px;
  }

  .message-scrollbar {
    display: none;
  }
}
</style>
