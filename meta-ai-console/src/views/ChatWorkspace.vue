<template>
  <div class="chat-page">
    <aside class="history-panel surface">
      <div class="history-controls">
        <n-button class="history-new-button" size="small" secondary @click="handleNewSession">
          新建对话
        </n-button>
      </div>
      <div class="panel-head">
        <strong>历史对话</strong>
        <n-button
            class="history-refresh-button"
            size="tiny"
            tertiary
            circle
            title="刷新历史对话"
            aria-label="刷新历史对话"
            :loading="chatListLoading"
            @click="loadChats"
        >
          <template #icon>
            <n-icon>
              <RefreshCw/>
            </n-icon>
          </template>
        </n-button>
      </div>
      <div class="history-list">
        <div
            v-for="item in chats"
            :key="item.id"
            :class="['history-item', { active: item.id === activeMetaChatId, pinned: item.pinned }]"
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
                <div class="section-label">知识库引用</div>
                <div class="citation-list">
                  <button
                      v-for="reference in messageItem.references"
                      :key="reference.documentId"
                      class="citation-item"
                      type="button"
                      @click="downloadReference(reference)"
                  >
                    {{ reference.documentName }}
                  </button>
                </div>
              </div>

              <div v-if="messageItem.role === 'user' && messageItem.files?.length" class="references session-files">
                <div class="section-label">附件</div>
                <div class="citation-list">
                  <span
                      v-for="file in messageItem.files"
                      :key="file.fileId"
                      class="citation-item file-source"
                  >
                    {{ file.fileName }}
                  </span>
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
        <input
            ref="chatFileInputRef"
            class="chat-file-input"
            type="file"
            multiple
            @change="handleChatFileChange"
        />
        <div v-if="visibleChatFiles.length || showRagContextScope" class="composer-context-row">
          <div v-if="visibleChatFiles.length" class="chat-file-strip">
            <span
                v-for="file in visibleChatFiles"
                :key="file.key"
                :class="['chat-file-chip', `is-${file.status}`, { selected: file.selected, selectable: file.selectable }]"
                :title="fileDisplayTitle(file)"
                role="button"
                tabindex="0"
                @click="toggleChatFileSelection(file)"
                @keydown.enter.prevent="toggleChatFileSelection(file)"
                @keydown.space.prevent="toggleChatFileSelection(file)"
            >
              <span class="chat-file-indicator" aria-hidden="true"></span>
              <span class="chat-file-name">{{ file.fileName }}</span>
              <span class="chat-file-status">{{ fileStatusText(file) }}</span>
            </span>
          </div>
          <div v-if="showRagContextScope" class="context-scope-row">
            <span class="context-scope-label">回答范围</span>
            <n-radio-group v-model:value="ragContextScope" class="context-scope-toggle" size="small">
              <n-radio-button value="FILES_ONLY">附件</n-radio-button>
              <n-radio-button value="FILES_AND_KNOWLEDGE">附件 + 知识库</n-radio-button>
            </n-radio-group>
          </div>
        </div>
        <n-input
            class="composer-input"
            v-model:value="draft"
            type="textarea"
            placeholder="输入问题，Enter 发送，Shift + Enter 换行"
            :autosize="{ minRows: 2, maxRows: 5 }"
            @keydown.enter="handleEnter"
        />
        <n-button
            class="composer-button attachment-button"
            secondary
            :loading="chatFileUploading"
            :disabled="sending || fileContextBusy"
            @click="openChatFilePicker"
        >
          <template #icon>
            <n-icon>
              <FileUp/>
            </n-icon>
          </template>
          附件
        </n-button>
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
            :disabled="!canSend"
            @click="sendMessage"
        >
          <template #icon>
            <n-icon>
              <Send/>
            </n-icon>
          </template>
          {{ sendButtonText }}
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
  FileUp,
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
  fetchChatFiles,
  fetchChatHistoryByChatId,
  fetchChats,
  renameChat,
  streamPlainChatJson,
  streamRagChatJson,
  updateChatFlags,
  uploadChatFiles,
} from '@/api/chat'
import {fetchStorageDocuments} from '@/api/storage'
import {useWorkspaceStore} from '@/stores/workspace'
import type {
  ChatContextScope,
  ChatOptions,
  MetaChat,
  MetaChatFileItem,
  MetaChatFileParseStatus,
  MetaContextFile,
  RetrievalDocumentReference,
  RetrievalTrace,
  StorageDocument
} from '@/types/api'
import type {ChatStreamHandle} from '@/api/chat'

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  time: string
  typing?: boolean
  references?: RetrievalDocumentReference[]
  files?: MetaContextFile[]
  trace?: RetrievalTrace
}

type ChatFileItemStatus = 'uploading' | 'parsing' | 'ready' | 'failed'

interface ChatFileItem {
  key: string
  fileId?: string
  fileName: string
  documentType?: string
  status: ChatFileItemStatus
  selected?: boolean
  selectable?: boolean
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
const chatFileInputRef = ref<HTMLInputElement | null>(null)
const messages = ref<ChatMessage[]>([])
const chats = ref<MetaChat[]>([])
const chatFiles = ref<MetaChatFileItem[]>([])
const pendingChatFilePlaceholders = ref<ChatFileItem[]>([])
const selectedChatFileIds = ref<string[]>([])
const ragContextScope = ref<ChatContextScope>('FILES_ONLY')
const activeMetaChatId = ref<string | null>(null)
const draft = ref('')
const sending = ref(false)
const uploadingChatFileScopeKey = ref<string | null>(null)
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

const activeChat = computed(() => chats.value.find((item) => item.id === activeMetaChatId.value) || null)
const visibleChatFiles = computed<ChatFileItem[]>(() => [
  ...pendingChatFilePlaceholders.value,
  ...chatFiles.value.map((file) => ({
    key: file.fileId,
    fileId: file.fileId,
    fileName: file.fileName,
    documentType: file.documentType,
    status: chatFileStatus(file.parseStatus),
    selected: selectedChatFileIds.value.includes(file.fileId),
    selectable: file.parseStatus === 'READY',
  })),
])
const currentFileScopeKey = computed(() => fileScopeKey({
  chatId: workspace.chatId,
  tenantId: workspace.tenantId,
  userId: workspace.userId,
}))
const chatFileUploading = computed(() => uploadingChatFileScopeKey.value === currentFileScopeKey.value)
const fileContextBusy = computed(() => chatFileUploading.value || visibleChatFiles.value.some((file) =>
    file.status === 'uploading' || file.status === 'parsing'))
const selectedReadyFileCount = computed(() => selectedReadyChatFiles().length)
const showRagContextScope = computed(() => chatMode.value === 'rag' && selectedReadyFileCount.value > 0)
const canSend = computed(() => Boolean(draft.value.trim()) && !sending.value && !fileContextBusy.value)
const sendButtonText = computed(() => fileContextBusy.value ? '处理中' : '发送')
let stopActiveTyping: (() => void) | null = null
let activeTypingRenderer: TypingRenderer | null = null
let activeStream: ChatStreamHandle | null = null
let streamStoppedByUser = false
let messageResizeObserver: ResizeObserver | null = null
let messageScrollbarHideTimer: number | null = null
let arrowScrollDelayTimer: number | null = null
let arrowScrollIntervalTimer: number | null = null
let chatFilePollingTimer: number | null = null
let draggingThumb = false
let thumbDragStartY = 0
let thumbDragStartTop = 0

onMounted(async () => {
  await nextTick()
  setupMessageScrollbar()
  await loadChats()
  if (activeMetaChatId.value && messages.value.length === 0) {
    await loadHistory()
    await loadChatFiles()
  }
})

onUnmounted(() => {
  stopStreaming()
  stopChatFilePolling()
  teardownMessageScrollbar()
})

watch(() => workspace.contextVersion, async () => {
  stopStreaming()
  activeMetaChatId.value = null
  messages.value = []
  chatFiles.value = []
  pendingChatFilePlaceholders.value = []
  selectedChatFileIds.value = []
  resetRagContextScope()
  uploadingChatFileScopeKey.value = null
  stopChatFilePolling()
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
 * 普通聊天调用 /v1/chat 并通过 stream=true 开启流式
 * RAG 聊天调用 /v1/rag 并通过 stream=true 开启流式，同时展示文件引用
 */
async function sendMessage() {
  const content = draft.value.trim()
  if (fileContextBusy.value) {
    message.warning('文件处理完成后再发送')
    return
  }
  if (sending.value || !content) return
  if (content) {
    draft.value = ''
  }
  await sendMessageContent(content)
}

async function sendMessageContent(content: string) {
  if (!content || sending.value) return
  if (fileContextBusy.value) {
    message.warning('文件处理完成后再发送')
    return
  }

  workspace.persist()
  stopStreaming()
  const selectedFiles = selectedReadyChatFiles()
  messages.value.push(createMessage('user', content, undefined, undefined, undefined, selectedFiles))
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
      workspace.setChatId(payload.chatId)
    },
    onDelta: (payload: { content: string }) => {
      if (streamStoppedByUser) return
      typingRenderer.enqueue(payload.content || '')
    },
    onDone: (payload: { answer?: string, references?: RetrievalDocumentReference[], files?: MetaContextFile[] }) => {
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

  const selectedFileIds = selectedFiles.map((file) => file.fileId)
  if (chatMode.value === 'plain') {
    activeStream = streamPlainChatJson(chatOptions(content, selectedFileIds.length), selectedFileIds, handlers)
  } else {
    activeStream = streamRagChatJson(chatOptions(content, selectedFileIds.length), selectedFileIds, handlers)
  }
  selectedChatFileIds.value = []
  resetRagContextScope()
}

function chatOptions(content: string, selectedFileCount: number): ChatOptions {
  return {
    chatId: workspace.chatId,
    msg: content,
    tenantId: workspace.tenantId,
    kbId: workspace.kbId,
    userId: workspace.userId,
    deptIds: workspace.deptIds,
    documentId: ragForm.documentId || undefined,
    documentType: ragForm.documentType || undefined,
    contextScope: resolveRequestContextScope(selectedFileCount),
  }
}

/**
 * 解析本轮请求上下文范围
 *
 * <p>
 * 普通聊天只表达附件上下文，RAG 有附件时默认附件优先，只有用户显式切换时才混合知识库
 */
function resolveRequestContextScope(selectedFileCount: number): ChatContextScope {
  if (chatMode.value === 'plain') {
    return 'FILES_ONLY'
  }
  if (selectedFileCount <= 0) {
    return 'KNOWLEDGE_ONLY'
  }
  return ragContextScope.value
}

/**
 * 重置 RAG 附件范围选择
 *
 * <p>
 * 每轮发送后回到附件优先，避免上轮“附件 + 知识库”选择无感影响下一轮文件问答
 */
function resetRagContextScope() {
  ragContextScope.value = 'FILES_ONLY'
}

/**
 * 判断文件状态请求是否仍属于当前会话
 *
 * <p>
 * 上传和轮询都是异步返回，必须用 tenantId、userId 和 chatId 防止切换会话后串位
 */
function isCurrentFileScope(scope: { chatId: string, tenantId: string, userId: string }) {
  return scope.chatId === workspace.chatId && scope.tenantId === workspace.tenantId && scope.userId === workspace.userId
}

/**
 * 构造会话文件状态作用域 key
 *
 * <p>
 * 上传中状态按租户、用户和会话隔离，避免长耗时上传影响切换后的其他会话
 */
function fileScopeKey(scope: { chatId: string, tenantId: string, userId: string }) {
  return `${scope.tenantId}:${scope.userId}:${scope.chatId}`
}

/**
 * 当前本轮选中的 READY 会话文件
 *
 * <p>
 * 输入框上方展示的是会话文件池，只有用户显式选中的 READY 文件才进入本轮 fileIds
 */
function selectedReadyChatFiles() {
  const selected = new Set(selectedChatFileIds.value)
  return chatFiles.value
      .filter((file) => file.parseStatus === 'READY' && selected.has(file.fileId))
      .map((file) => ({
        fileId: file.fileId,
        fileName: file.fileName,
        documentType: file.documentType || '',
      }))
}

/**
 * 切换本轮会话文件选择状态
 *
 * <p>
 * 只有 READY 文件可以选择，解析中和失败文件只展示状态，不参与本轮问答
 *
 * @param file 文件展示项
 */
function toggleChatFileSelection(file: ChatFileItem) {
  if (!file.fileId || !file.selectable) return
  if (selectedChatFileIds.value.includes(file.fileId)) {
    selectedChatFileIds.value = selectedChatFileIds.value.filter((id) => id !== file.fileId)
    if (selectedChatFileIds.value.length === 0) {
      resetRagContextScope()
    }
    return
  }
  selectedChatFileIds.value = [...selectedChatFileIds.value, file.fileId]
  if (selectedChatFileIds.value.length === 1) {
    resetRagContextScope()
  }
}

/**
 * 合并会话文件状态列表
 *
 * <p>
 * 上传接口返回的是新文件，轮询接口返回的是服务端全量文件
 * 这里按 fileId 去重，避免上传完成到首次轮询之间出现重复 chip
 */
function mergeChatFiles(currentFiles: MetaChatFileItem[], nextFiles: MetaChatFileItem[]) {
  const merged = new Map<string, MetaChatFileItem>()
  currentFiles.forEach((file) => merged.set(file.fileId, file))
  nextFiles.forEach((file) => merged.set(file.fileId, file))
  return Array.from(merged.values()).sort((left, right) => toTime(right.createdAt) - toTime(left.createdAt))
}

/**
 * 合并本轮已选文件 ID
 *
 * <p>
 * 上传完成的新文件默认加入本轮选择，历史文件需要用户手动点击选择
 *
 * @param currentIds 当前已选文件 ID
 * @param nextIds    需要追加的文件 ID
 * @return 去重后的文件 ID
 */
function mergeSelectedFileIds(currentIds: string[], nextIds: string[]) {
  return Array.from(new Set([...currentIds, ...nextIds.filter(Boolean)]))
}

/**
 * 清理已经不在当前会话文件池中的选择
 *
 * <p>
 * 轮询返回全量文件状态后执行，避免本轮选择引用不存在的 fileId
 */
function pruneSelectedChatFiles() {
  const fileIds = new Set(chatFiles.value.map((file) => file.fileId))
  selectedChatFileIds.value = selectedChatFileIds.value.filter((fileId) => fileIds.has(fileId))
  if (selectedChatFileIds.value.length === 0) {
    resetRagContextScope()
  }
}

/**
 * 启动当前会话文件状态轮询
 *
 * <p>
 * 只有存在 UPLOADED 或 PARSING 文件时才需要轮询，READY 和 PARSE_FAILED 都是终态
 */
function startChatFilePolling() {
  if (chatFilePollingTimer !== null || !hasProcessingChatFiles()) return
  chatFilePollingTimer = window.setInterval(() => {
    loadChatFiles()
  }, 2500)
}

/**
 * 根据最新文件状态更新轮询开关
 */
function updateChatFilePolling() {
  if (hasProcessingChatFiles()) {
    startChatFilePolling()
    return
  }
  stopChatFilePolling()
}

/**
 * 停止文件状态轮询
 */
function stopChatFilePolling() {
  if (chatFilePollingTimer !== null) {
    window.clearInterval(chatFilePollingTimer)
    chatFilePollingTimer = null
  }
}

/**
 * 判断当前会话是否仍有处理中附件
 */
function hasProcessingChatFiles() {
  return chatFiles.value.some((file) => file.parseStatus === 'UPLOADED' || file.parseStatus === 'PARSING')
}

function openChatFilePicker() {
  chatFileInputRef.value?.click()
}

async function handleChatFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (!files.length || chatFileUploading.value) return

  workspace.persist()
  const uploadScope = {
    chatId: workspace.chatId,
    tenantId: workspace.tenantId,
    userId: workspace.userId,
  }
  uploadingChatFileScopeKey.value = fileScopeKey(uploadScope)
  // 上传请求返回 fileId 前先用本地占位展示，返回后切换为服务端 parseStatus 驱动
  pendingChatFilePlaceholders.value = files.map((file, index) => ({
    key: `uploading-${Date.now()}-${index}-${file.name}`,
    fileName: file.name,
    documentType: file.name.includes('.') ? file.name.split('.').pop() : undefined,
    status: 'uploading',
  }))
  try {
    const uploadedFiles = await uploadChatFiles(uploadScope.chatId, uploadScope.tenantId, uploadScope.userId, files)
    if (isCurrentFileScope(uploadScope)) {
      chatFiles.value = mergeChatFiles(chatFiles.value, uploadedFiles)
      selectedChatFileIds.value = mergeSelectedFileIds(selectedChatFileIds.value, uploadedFiles.map((file) => file.fileId))
      resetRagContextScope()
      pendingChatFilePlaceholders.value = []
      startChatFilePolling()
    }
    await loadChatFiles(uploadScope)
    message.success('会话文件已上传')
  } catch (error) {
    message.error(error instanceof Error ? error.message : '上传会话文件失败')
  } finally {
    if (isCurrentFileScope(uploadScope)) {
      pendingChatFilePlaceholders.value = []
      uploadingChatFileScopeKey.value = null
    }
  }
}

/**
 * 加载会话 READY 文件
 *
 * <p>
 * 未传 scope 时读取当前工作区，上传完成回刷时传入上传开始时的 scope，避免切换会话后串位
 */
async function loadChatFiles(scope = {
  chatId: workspace.chatId,
  tenantId: workspace.tenantId,
  userId: workspace.userId,
}) {
  try {
    const files = await fetchChatFiles(scope.chatId, scope.tenantId, scope.userId)
    if (isCurrentFileScope(scope)) {
      chatFiles.value = files
      pruneSelectedChatFiles()
      updateChatFilePolling()
    }
  } catch {
    if (isCurrentFileScope(scope)) {
      chatFiles.value = []
      stopChatFilePolling()
    }
  }
}

function openRetrievalScope() {
  advancedVisible.value = true
  loadScopeDocuments()
}

async function loadScopeDocuments() {
  if (scopeDocumentsLoading.value) return

  workspace.persist()
  if (!workspace.tenantId || !workspace.kbId) {
    scopeDocuments.value = []
    return
  }

  scopeDocumentsLoading.value = true
  try {
    const page = await fetchStorageDocuments({
      tenantId: workspace.tenantId,
      kbId: workspace.kbId,
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
async function loadHistory(chat: MetaChat | null = activeChat.value) {
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
            parseReferences(item.reference),
            undefined,
            item.createdAt,
            parseFiles(item.files),
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
 * 只刷新 chatId
 * 当前租户、知识库和用户保持不变
 */
function handleNewSession() {
  stopStreaming()
  stopChatFilePolling()
  workspace.resetSession()
  activeMetaChatId.value = null
  messages.value = []
  chatFiles.value = []
  pendingChatFilePlaceholders.value = []
  selectedChatFileIds.value = []
  resetRagContextScope()
  uploadingChatFileScopeKey.value = null
}

async function loadChats() {
  chatListLoading.value = true
  try {
    const page = await fetchChats(workspace.tenantId, workspace.userId)
    chats.value = page.records
    sortChats()
    const current = chats.value.find((item) => item.chatId === workspace.chatId)
    if (current) {
      activeMetaChatId.value = current.id
      await loadChatFiles()
    }
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载对话失败')
  } finally {
    chatListLoading.value = false
  }
}

async function selectChat(chat: MetaChat) {
  stopStreaming()
  stopChatFilePolling()
  pendingChatFilePlaceholders.value = []
  selectedChatFileIds.value = []
  resetRagContextScope()
  activeMetaChatId.value = chat.id
  workspace.selectChat(chat)
  await loadHistory(chat)
  await loadChatFiles()
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
    if (activeMetaChatId.value === chat.id) {
      activeMetaChatId.value = null
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
  if (fileContextBusy.value) {
    message.warning('文件处理完成后再发送')
    return
  }
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
 * 文件状态展示文案
 *
 * <p>
 * READY 文件展示的是本轮是否参与回答，而不是文件能否被点击
 */
function fileStatusText(file: ChatFileItem) {
  if (file.status === 'ready') {
    return file.selected ? '已选' : '未选'
  }
  const statusText: Record<ChatFileItemStatus, string> = {
    uploading: '上传中',
    parsing: '解析中',
    ready: '已就绪',
    failed: '解析失败',
  }
  return statusText[file.status]
}

/**
 * 转换后端解析状态为前端展示状态
 *
 * <p>
 * READY 文件可被用户选择，其他状态只展示，不参与问答上下文
 */
function chatFileStatus(status: MetaChatFileParseStatus): ChatFileItemStatus {
  if (status === 'READY') return 'ready'
  if (status === 'PARSE_FAILED') return 'failed'
  return 'parsing'
}

/**
 * 文件状态提示
 *
 * @param file 文件展示项
 * @return 鼠标悬停提示
 */
function fileDisplayTitle(file: ChatFileItem) {
  const type = shouldShowDocumentType(file) ? ` · ${file.documentType}` : ''
  return `${file.fileName}${type} · ${fileStatusText(file)}`
}

/**
 * 判断悬浮提示是否需要补充文档类型
 *
 * <p>
 * 文件名已经包含同名扩展名时不重复展示 documentType，避免出现 file.docx · docx
 *
 * @param file 文件展示项
 * @return true 表示需要追加文档类型
 */
function shouldShowDocumentType(file: ChatFileItem) {
  const documentType = file.documentType?.trim()
  if (!documentType) return false
  const normalizedType = documentType.replace(/^\./, '').toLowerCase()
  if (!normalizedType) return false
  return !file.fileName.toLowerCase().endsWith(`.${normalizedType}`)
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
    references?: RetrievalDocumentReference[],
    trace?: RetrievalTrace,
    createdAt?: string,
    files?: MetaContextFile[],
): ChatMessage {
  return {
    id: crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`,
    role,
    content,
    references,
    files,
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

function parseReferences(value?: string): RetrievalDocumentReference[] | undefined {
  if (!value) return undefined
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : undefined
  } catch {
    return undefined
  }
}

function parseFiles(value?: string): MetaContextFile[] | undefined {
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
  if (normalized === 'file_chat') {
    return '附件问答'
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
  if (normalized === 'file_chat') {
    return 'file'
  }
  return 'default'
}

async function downloadReference(reference: RetrievalDocumentReference) {
  try {
    const response = await fetch(`/api/v1/storage/documents/download/${encodeURIComponent(reference.documentId)}`)
    if (!response.ok) {
      throw new Error(`下载失败：${response.status}`)
    }
    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    const link = window.document.createElement('a')
    link.href = url
    link.download = reference.documentName
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
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.panel-head strong {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #e7fff8;
  font-size: 15px;
  font-weight: 800;
  line-height: 1.4;
  text-shadow: 0 0 14px rgba(65, 214, 183, 0.12);
}

.panel-head strong::before {
  width: 3px;
  height: 14px;
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(65, 214, 183, 0.95), rgba(65, 214, 183, 0.38));
  content: "";
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
  --n-text-color-hover: #b9fff0 !important;
  --n-text-color-pressed: #8eeed9 !important;
  --n-text-color-focus: #b9fff0 !important;
  flex: none;
  width: 28px;
  height: 28px;
  color: #93a0b1;
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

.history-mode.file {
  border-color: rgba(247, 201, 72, 0.26);
  color: #f3d275;
  background: rgba(247, 201, 72, 0.1);
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

.file-source {
  cursor: default;
}

.session-files .citation-item {
  border-color: rgba(126, 168, 255, 0.24);
  color: #c9d8ff;
  background: rgba(126, 168, 255, 0.08);
}

.composer-context-row {
  grid-column: 1 / -1;
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px 14px;
}

.context-scope-row {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  margin-left: auto;
  padding-top: 1px;
}

.context-scope-label {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 78px;
  height: 30px;
  border: 1px solid rgba(65, 214, 183, 0.3);
  border-right: 0;
  border-radius: 6px 0 0 6px;
  color: #e7fff8;
  font-size: 13px;
  font-weight: 800;
  line-height: 30px;
  text-shadow: 0 0 14px rgba(65, 214, 183, 0.12);
  background: rgba(65, 214, 183, 0.1);
}

.context-scope-toggle {
  display: inline-flex;
  align-items: center;
  height: 30px;
  min-height: 30px;
  border: 1px solid rgba(65, 214, 183, 0.3);
  border-radius: 0 6px 6px 0;
  overflow: hidden;
  background: rgba(15, 23, 42, 0.42);
}

.context-scope-toggle :deep(.n-radio-button) {
  --n-button-border-color: transparent !important;
  --n-button-border-color-active: transparent !important;
  --n-button-border-color-hover: transparent !important;
  --n-button-box-shadow-focus: 0 0 0 2px rgba(45, 212, 191, 0.12) !important;
  --n-button-color: transparent !important;
  --n-button-color-active: rgba(65, 214, 183, 0.18) !important;
  --n-button-color-hover: rgba(65, 214, 183, 0.08) !important;
  --n-button-text-color: #a9bac8 !important;
  --n-button-text-color-active: #d7fff7 !important;
  display: inline-flex !important;
  flex: 0 0 94px;
  align-items: center;
  justify-content: center;
  width: 94px;
  min-width: 94px;
  height: 30px;
  border: 0 !important;
  border-left: 0 !important;
  border-radius: 0 !important;
  padding: 0 7px;
  font-size: 12px;
  font-weight: 700;
  line-height: 30px !important;
  color: #a9bac8;
  background: transparent;
  box-shadow: none !important;
}

.context-scope-toggle :deep(.n-radio-button + .n-radio-button) {
  margin-left: 0 !important;
  border-left: 0 !important;
}

.context-scope-toggle :deep(.n-radio-button::before),
.context-scope-toggle :deep(.n-radio-button::after),
.context-scope-toggle :deep(.n-radio-button__state-border) {
  display: none !important;
  width: 0 !important;
  border: 0 !important;
  opacity: 0 !important;
}

.context-scope-toggle :deep(.n-radio-button__label) {
  display: block;
  width: 100%;
  padding: 0;
  overflow: hidden;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 30px;
}

.context-scope-toggle :deep(.n-radio-button.n-radio-button--checked) {
  color: #d7fff7;
  background: rgba(65, 214, 183, 0.24);
  box-shadow: none !important;
}

.composer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.chat-file-input {
  display: none;
}

.chat-file-strip {
  display: flex;
  flex: 1 1 520px;
  flex-wrap: wrap;
  min-width: 0;
  gap: 8px;
}

.chat-file-chip {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: min(250px, 100%);
  border: 1px solid rgba(126, 168, 255, 0.24);
  border-radius: 6px;
  padding: 5px 9px;
  overflow: hidden;
  color: #c9d8ff;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: rgba(126, 168, 255, 0.08);
}

.chat-file-chip.selectable {
  cursor: pointer;
}

.chat-file-chip.selectable:hover {
  border-color: rgba(45, 212, 191, 0.45);
  background: rgba(45, 212, 191, 0.08);
}

.chat-file-chip.selected {
  border-color: rgba(45, 212, 191, 0.62);
  color: #d9fffb;
  background: rgba(45, 212, 191, 0.14);
}

.chat-file-chip.selected .chat-file-status {
  color: #8ef7e7;
}

.chat-file-chip.is-parsing {
  border-color: rgba(255, 203, 104, 0.35);
  color: #ffe2a3;
  background: rgba(255, 203, 104, 0.1);
}

.chat-file-chip.is-parsing::after {
  position: absolute;
  inset: 0;
  content: "";
  pointer-events: none;
  background: linear-gradient(
      90deg,
      transparent 0%,
      rgba(255, 226, 163, 0.08) 42%,
      rgba(255, 226, 163, 0.22) 50%,
      rgba(255, 226, 163, 0.08) 58%,
      transparent 100%
  );
  transform: translateX(-110%);
  animation: chat-file-parsing-scan 1.65s ease-in-out infinite;
}

.chat-file-chip.is-uploading {
  border-color: rgba(126, 168, 255, 0.32);
  color: #d4e0ff;
  background: rgba(126, 168, 255, 0.1);
}

.chat-file-chip.is-failed {
  border-color: rgba(255, 117, 117, 0.36);
  color: #ffc4c4;
  background: rgba(255, 117, 117, 0.1);
}

.chat-file-indicator {
  position: relative;
  z-index: 1;
  flex: 0 0 8px;
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #7ea8ff;
  box-shadow: 0 0 0 2px rgba(126, 168, 255, 0.14);
}

.chat-file-chip.is-uploading .chat-file-indicator {
  background: #9ebcff;
  box-shadow: 0 0 0 0 rgba(126, 168, 255, 0.34);
  animation: chat-file-uploading-breathe 1.18s ease-in-out infinite;
}

.chat-file-chip.is-parsing .chat-file-indicator {
  background: #ffcb68;
  box-shadow: 0 0 0 0 rgba(255, 203, 104, 0.38);
  animation: chat-file-parsing-pulse 1.18s ease-out infinite;
}

.chat-file-chip.is-ready .chat-file-indicator {
  background: #76e0b6;
  box-shadow: 0 0 0 2px rgba(118, 224, 182, 0.14);
}

.chat-file-chip.is-failed .chat-file-indicator {
  background: #ff7575;
  box-shadow: 0 0 0 2px rgba(255, 117, 117, 0.16);
}

.chat-file-name {
  position: relative;
  z-index: 1;
  flex: 1 1 auto;
  min-width: 0;
  margin-right: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chat-file-status {
  position: relative;
  z-index: 1;
  flex: 0 0 auto;
  margin-left: auto;
  color: rgba(255, 255, 255, 0.62);
  font-size: 11px;
}

@keyframes chat-file-uploading-breathe {
  0%,
  100% {
    opacity: 0.62;
    box-shadow: 0 0 0 0 rgba(126, 168, 255, 0.18);
  }
  50% {
    opacity: 1;
    box-shadow: 0 0 0 5px rgba(126, 168, 255, 0);
  }
}

@keyframes chat-file-parsing-pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(255, 203, 104, 0.42);
  }
  70% {
    box-shadow: 0 0 0 7px rgba(255, 203, 104, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(255, 203, 104, 0);
  }
}

@keyframes chat-file-parsing-scan {
  0% {
    transform: translateX(-110%);
  }
  54%,
  100% {
    transform: translateX(110%);
  }
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

.attachment-button {
  min-width: 86px;
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
