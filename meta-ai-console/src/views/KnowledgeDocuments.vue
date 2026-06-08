<template>
  <div class="documents-page">
    <div class="toolbar">
      <div class="toolbar-main">
        <n-input
            v-model:value="query.keyword"
            clearable
            placeholder="文件名关键字"
            style="width: 220px"
            @keyup.enter="loadDocuments"
        />
        <n-select
            v-model:value="query.indexStatus"
            clearable
            placeholder="索引状态"
            :options="indexStatusOptions"
            style="width: 150px"
        />
        <n-select
            v-model:value="query.visibility"
            clearable
            placeholder="可见性"
            :options="visibilityOptions"
            style="width: 140px"
        />
        <n-button type="primary" :loading="loading" @click="loadDocuments">
          查询
        </n-button>
        <n-button secondary @click="resetQuery">
          重置
        </n-button>
      </div>
      <div class="upload-actions">
        <n-button class="upload-toolbar-button" secondary @click="uploadPanelVisible = true">
          上传设置
        </n-button>
        <n-button class="upload-toolbar-button" type="primary" @click="openUploadDialog">
          <template #icon>
            <FileUp/>
          </template>
          上传文件
        </n-button>
      </div>
    </div>

    <n-data-table
        class="surface"
        :columns="columns"
        :data="documents"
        :loading="loading"
        :pagination="pagination"
        :bordered="false"
        remote
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
    />

    <n-drawer v-model:show="uploadPanelVisible" :width="420">
      <n-drawer-content title="上传设置">
        <n-form class="upload-form" label-placement="top">
          <div class="upload-form-grid upload-settings-grid">
            <n-form-item label="租户 ID">
              <n-input v-model:value="uploadDefaults.tenantId" placeholder="上传默认租户 ID"/>
            </n-form-item>
            <n-form-item label="知识库 ID">
              <n-input v-model:value="uploadDefaults.kbId" placeholder="上传默认知识库 ID"/>
            </n-form-item>
            <n-form-item label="可见性">
              <n-select v-model:value="uploadDefaults.visibility" :options="visibilityOptions"/>
            </n-form-item>
            <n-form-item label="部门 ID">
              <n-input v-model:value="uploadDefaults.deptId" placeholder="DEPT 时填写"/>
            </n-form-item>
            <n-form-item label="用户 ID">
              <n-input v-model:value="uploadDefaults.userId" placeholder="USER 时填写"/>
            </n-form-item>
            <n-form-item label="文档类型">
              <n-input v-model:value="uploadDefaults.documentType" placeholder="自动识别"/>
            </n-form-item>
          </div>
          <label class="upload-index-card">
            <span>
              <strong>上传后自动索引</strong>
              <small>上传成功后立即提交索引任务</small>
            </span>
            <n-switch v-model:value="uploadDefaults.autoIndex"/>
          </label>
        </n-form>
        <template #footer>
          <n-button @click="uploadPanelVisible = false">关闭</n-button>
        </template>
      </n-drawer-content>
    </n-drawer>

    <n-modal
        v-model:show="uploadDialogVisible"
        preset="card"
        title="上传文件"
        :bordered="false"
        :segmented="{ content: true, action: true }"
        :style="{ width: '520px', maxWidth: 'calc(100vw - 32px)' }"
        @after-leave="resetUploadDialog"
    >
      <n-form class="upload-form" label-placement="top">
        <div class="upload-form-grid">
          <n-form-item label="租户 ID">
            <n-input v-model:value="uploadDraft.tenantId" :disabled="uploading" placeholder="上传租户 ID"/>
          </n-form-item>
          <n-form-item label="知识库 ID">
            <n-input v-model:value="uploadDraft.kbId" :disabled="uploading" placeholder="上传知识库 ID"/>
          </n-form-item>
          <n-form-item label="可见性">
            <n-select v-model:value="uploadDraft.visibility" :disabled="uploading" :options="visibilityOptions"/>
          </n-form-item>
          <n-form-item label="部门 ID">
            <n-input v-model:value="uploadDraft.deptId" :disabled="uploading" placeholder="DEPT 时填写"/>
          </n-form-item>
          <n-form-item label="用户 ID">
            <n-input v-model:value="uploadDraft.userId" :disabled="uploading" placeholder="USER 时填写"/>
          </n-form-item>
          <n-form-item label="文档类型">
            <n-input v-model:value="uploadDraft.documentType" :disabled="uploading" placeholder="自动识别"/>
          </n-form-item>
        </div>
        <label class="upload-index-card">
          <span>
            <strong>上传后自动索引</strong>
            <small>上传成功后立即提交索引任务</small>
          </span>
          <n-switch v-model:value="uploadDraft.autoIndex" :disabled="uploading"/>
        </label>
        <div class="upload-file-card">
          <div class="upload-file-head">
            <strong>文件</strong>
            <span>单次上传 1 个文件</span>
          </div>
          <input
              ref="uploadFileInput"
              class="upload-native-input"
              type="file"
              :accept="uploadAccept"
              :disabled="uploading"
              @change="handleUploadFileChange"
          >
          <div class="upload-file-body">
            <n-button secondary :disabled="uploading" @click="openUploadFilePicker">
              <template #icon>
                <FileUp/>
              </template>
              选择文件
            </n-button>
            <div v-if="selectedUploadFile" class="upload-file-selected">
              <span>{{ selectedUploadFile.name }}</span>
              <small>{{ formatBytes(selectedUploadFile.size) }}</small>
              <n-button quaternary size="tiny" :disabled="uploading" @click="clearSelectedUploadFile">
                移除
              </n-button>
            </div>
            <div v-else class="upload-file-empty">尚未选择文件</div>
          </div>
        </div>
      </n-form>
      <template #footer>
        <div class="upload-dialog-actions">
          <n-button tertiary :disabled="uploading" @click="uploadDialogVisible = false">
            取消
          </n-button>
          <n-button type="primary" :loading="uploading" :disabled="!canUpload" @click="submitUpload">
            上传
          </n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import {computed, h, onMounted, reactive, ref} from 'vue'
import {
  NButton,
  NTag,
  NSpace,
  type DataTableColumns,
  useMessage,
} from 'naive-ui'
import {FileUp} from 'lucide-vue-next'

import {
  downloadDocument,
  fetchStorageDocuments,
  indexDocument,
  uploadDocument,
} from '@/api/storage'
import {useWorkspaceStore} from '@/stores/workspace'
import type {StorageDocument} from '@/types/api'

const message = useMessage()
const workspace = useWorkspaceStore()
const loading = ref(false)
const documents = ref<StorageDocument[]>([])
const total = ref(0)
const uploadPanelVisible = ref(false)
const uploadDialogVisible = ref(false)
const uploading = ref(false)
const uploadFileInput = ref<HTMLInputElement | null>(null)
const selectedUploadFile = ref<File | null>(null)
const uploadAccept = '.doc,.docx,.pdf,.txt,.md,.json,.csv,.xlsx,.xls'

const query = reactive({
  keyword: '',
  indexStatus: undefined as string | undefined,
  visibility: undefined as string | undefined,
  current: 1,
  size: 20,
})

const uploadDefaults = reactive({
  tenantId: workspace.tenantId,
  kbId: workspace.kbId,
  visibility: 'PUBLIC',
  deptId: '',
  userId: '',
  documentType: '',
  autoIndex: true,
})

const uploadDraft = reactive({
  tenantId: workspace.tenantId,
  kbId: workspace.kbId,
  visibility: 'PUBLIC',
  deptId: '',
  userId: '',
  documentType: '',
  autoIndex: true,
})

const indexStatusOptions = [
  {label: '已上传', value: 'UPLOADED'},
  {label: '索引中', value: 'INDEXING'},
  {label: '已索引', value: 'INDEXED'},
  {label: '索引失败', value: 'INDEX_FAILED'},
]

const visibilityOptions = [
  {label: '公开', value: 'PUBLIC'},
  {label: '部门', value: 'DEPT'},
  {label: '用户', value: 'USER'},
]

/**
 * Naive UI 表格分页配置
 *
 * <p>
 * remote = true 时，分页变化不会自动切数据
 * 需要在 handlePageChange / handlePageSizeChange 中重新请求后端
 */
const pagination = computed(() => ({
  page: query.current,
  pageSize: query.size,
  itemCount: total.value,
  showSizePicker: true,
  pageSizes: [
    {label: '10 条/页', value: 10},
    {label: '20 条/页', value: 20},
    {label: '50 条/页', value: 50},
    {label: '100 条/页', value: 100},
  ],
  prev: () => '上一页',
  next: () => '下一页',
}))

const canUpload = computed(() => Boolean(
    selectedUploadFile.value &&
    uploadDraft.tenantId.trim() &&
    uploadDraft.kbId.trim() &&
    !uploading.value,
))

/**
 * 文件表格列定义
 *
 * <p>
 * render 用于把后端原始字段转成更适合阅读的 UI
 * 例如 fileSize 转成 KB / MB，indexStatus 转成彩色状态标签
 */
const columns: DataTableColumns<StorageDocument> = [
  {
    title: '文件名',
    key: 'originalFilename',
    ellipsis: {
      tooltip: true,
    },
    minWidth: 220,
  },
  {
    title: '类型',
    key: 'documentType',
    width: 96,
    align: 'center',
    render: (row) => row.documentType || '-',
  },
  {
    title: '大小',
    key: 'fileSize',
    width: 110,
    align: 'center',
    render: (row) => formatBytes(row.fileSize),
  },
  {
    title: '可见性',
    key: 'visibility',
    width: 100,
    align: 'center',
    render: (row) => h(NTag, {size: 'small', bordered: false}, {default: () => formatVisibility(row.visibility)}),
  },
  {
    title: '索引状态',
    key: 'indexStatus',
    width: 130,
    align: 'center',
    render: (row) =>
        h(
            NTag,
            {
              size: 'small',
              color: statusColor(row.indexStatus),
            },
            {default: () => formatIndexStatus(row.indexStatus)},
        ),
  },
  {
    title: '分片数',
    key: 'chunkCount',
    width: 90,
    align: 'center',
  },
  {
    title: '更新时间',
    key: 'updatedAt',
    width: 180,
    align: 'center',
    render: (row) => formatDate(row.updatedAt),
  },
  {
    title: '操作',
    key: 'actions',
    width: 220,
    fixed: 'right',
    align: 'center',
    render: (row) =>
        h(NSpace, {size: 8, wrap: false, justify: 'center'}, () => [
          actionButton('下载', 'download', () => handleDownload(row)),
          actionButton('索引', 'index', () => handleIndex(row)),
          actionButton('删除', 'delete', undefined, true),
        ]),
  },
]

onMounted(loadDocuments)

/**
 * 加载知识库文件分页
 *
 * <p>
 * 查询条件来自顶部工作区上下文和当前页面筛选项
 */
async function loadDocuments() {
  loading.value = true
  workspace.persist()
  if (!uploadDefaults.tenantId) {
    uploadDefaults.tenantId = workspace.tenantId
  }
  if (!uploadDefaults.kbId) {
    uploadDefaults.kbId = workspace.kbId
  }
  try {
    const page = await fetchStorageDocuments({
      tenantId: workspace.tenantId,
      kbId: workspace.kbId,
      visibility: query.visibility,
      indexStatus: query.indexStatus,
      keyword: query.keyword || undefined,
      current: query.current,
      size: query.size,
    })
    documents.value = page.records
    total.value = page.total
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载文件失败')
  } finally {
    loading.value = false
  }
}

/**
 * 重置查询条件
 *
 * <p>
 * 只重置当前页面筛选项
 * 顶部工作区上下文保持不变
 */
function resetQuery() {
  query.keyword = ''
  query.indexStatus = undefined
  query.visibility = undefined
  query.current = 1
  loadDocuments()
}

/**
 * 打开上传弹窗
 *
 * <p>
 * 弹窗默认值来自上传设置
 * 本次上传可以临时修改，不反写上传设置
 */
function openUploadDialog() {
  uploadDraft.tenantId = uploadDefaults.tenantId || workspace.tenantId
  uploadDraft.kbId = uploadDefaults.kbId || workspace.kbId
  uploadDraft.visibility = uploadDefaults.visibility
  uploadDraft.deptId = uploadDefaults.deptId
  uploadDraft.userId = uploadDefaults.userId
  uploadDraft.documentType = uploadDefaults.documentType
  uploadDraft.autoIndex = uploadDefaults.autoIndex
  clearSelectedUploadFile()
  uploadDialogVisible.value = true
}

function openUploadFilePicker() {
  uploadFileInput.value?.click()
}

function handleUploadFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  selectedUploadFile.value = input.files?.[0] || null
}

function clearSelectedUploadFile() {
  selectedUploadFile.value = null
  if (uploadFileInput.value) {
    uploadFileInput.value.value = ''
  }
}

/**
 * 提交上传文件
 *
 * <p>
 * 当前版本按单文件上传设计
 * 选择文件和上传参数都来自弹窗内状态
 */
async function submitUpload() {
  const file = selectedUploadFile.value
  if (!file) {
    message.error('请选择上传文件')
    return
  }

  uploading.value = true
  try {
    await uploadDocument({
      tenantId: uploadDraft.tenantId.trim(),
      kbId: uploadDraft.kbId.trim(),
      visibility: uploadDraft.visibility,
      deptId: uploadDraft.deptId.trim() || undefined,
      userId: uploadDraft.userId.trim() || undefined,
      documentType: uploadDraft.documentType.trim() || undefined,
      autoIndex: uploadDraft.autoIndex,
      file,
    })
    message.success('上传成功')
    uploadDialogVisible.value = false
    query.current = 1
    await loadDocuments()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '上传失败')
  } finally {
    uploading.value = false
  }
}

/**
 * 重置上传弹窗
 */
function resetUploadDialog() {
  if (uploading.value) return
  clearSelectedUploadFile()
}

/**
 * 下载文件
 *
 * <p>
 * documentId 来自文件行数据
 * tenantId 和 kbId 来自顶部工作区上下文
 */
async function handleDownload(row: StorageDocument) {
  try {
    await downloadDocument(workspace.tenantId, workspace.kbId, row)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '下载失败')
  }
}

/**
 * 手动提交索引任务
 *
 * <p>
 * 后端返回后只是代表任务已提交
 * 页面会重新加载列表来查看最新状态
 */
async function handleIndex(row: StorageDocument) {
  try {
    await indexDocument(workspace.tenantId, workspace.kbId, row.documentId)
    message.success('索引任务已提交')
    await loadDocuments()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '索引提交失败')
  }
}

/**
 * 页码变化
 */
function handlePageChange(page: number) {
  query.current = page
  loadDocuments()
}

/**
 * 每页数量变化
 */
function handlePageSizeChange(pageSize: number) {
  query.size = pageSize
  query.current = 1
  loadDocuments()
}

/**
 * 构造表格操作区的文字按钮
 *
 * @example
 * actionButton('下载', 'download', () => handleDownload(row))
 */
function actionButton(
    label: string,
    variant: 'download' | 'index' | 'delete',
    onClick?: () => void,
    disabled = false,
) {
  return h(
      NButton,
      {
        size: 'small',
        secondary: true,
        type: actionButtonType(variant),
        class: ['document-action-button', `document-action-button--${variant}`],
        disabled,
        onClick,
      },
      {default: () => label},
  )
}

function actionButtonType(variant: 'download' | 'index' | 'delete') {
  if (variant === 'download') return 'primary'
  if (variant === 'index') return 'info'
  return 'error'
}

function formatVisibility(value: string) {
  if (value === 'PUBLIC') return '公开'
  if (value === 'DEPT') return '部门可见'
  if (value === 'USER') return '用户可见'
  return value || '-'
}

function formatIndexStatus(value: string) {
  if (value === 'UPLOADED') return '已上传'
  if (value === 'INDEXING') return '索引中'
  if (value === 'INDEXED') return '已索引'
  if (value === 'INDEX_FAILED') return '索引失败'
  return value || '-'
}

/**
 * 索引状态颜色
 *
 * <p>
 * INDEXED 绿色
 * INDEXING 黄色
 * INDEX_FAILED 红色
 */
function statusColor(status: string) {
  if (status === 'INDEXED') return {color: '#143c35', textColor: '#72f1d4'}
  if (status === 'INDEX_FAILED') return {color: '#4a1d24', textColor: '#ff8f9c'}
  if (status === 'INDEXING') return {color: '#3c3214', textColor: '#ffd36a'}
  return {color: '#243142', textColor: '#a9bad0'}
}

/**
 * 字节数格式化
 *
 * @example
 * formatBytes(2048) === '2.0 KB'
 */
function formatBytes(value: number) {
  if (!value) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let size = value
  let unitIndex = 0
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex += 1
  }
  return `${size.toFixed(size >= 10 ? 0 : 1)} ${units[unitIndex]}`
}

/**
 * 时间格式化
 */
function formatDate(value: string) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hours = `${date.getHours()}`.padStart(2, '0')
  const minutes = `${date.getMinutes()}`.padStart(2, '0')
  const seconds = `${date.getSeconds()}`.padStart(2, '0')
  return `${date.getFullYear()}年${month}月${day}日 ${hours}:${minutes}:${seconds}`
}
</script>

<style scoped>
.documents-page {
  min-width: 860px;
}

.upload-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.upload-toolbar-button {
  min-width: 120px;
  height: 40px;
}

.document-action-button {
  min-width: 52px;
}

.document-action-button--delete {
  opacity: 0.68;
}

.upload-form {
  display: grid;
  gap: 14px;
}

.upload-form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  column-gap: 12px;
  row-gap: 2px;
}

.upload-settings-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.upload-index-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  min-height: 48px;
  border: 1px solid rgba(65, 214, 183, 0.18);
  border-radius: 8px;
  padding: 10px 12px;
  background: rgba(65, 214, 183, 0.06);
  cursor: pointer;
}

.upload-index-card span {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.upload-index-card strong {
  color: #f4f7fb;
  font-size: 13px;
  line-height: 1.35;
}

.upload-index-card small {
  color: #7f8b9b;
  font-size: 12px;
  line-height: 1.35;
}

.upload-file-card {
  display: grid;
  gap: 10px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 12px;
  background: rgba(255, 255, 255, 0.03);
}

.upload-file-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.upload-file-head strong {
  color: #f4f7fb;
  font-size: 14px;
}

.upload-file-head span {
  color: #7f8b9b;
  font-size: 12px;
}

.upload-native-input {
  display: none;
}

.upload-file-body {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 10px;
}

.upload-file-selected {
  display: grid;
  min-width: 0;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 8px;
  border-radius: 7px;
  padding: 6px 8px;
  background: rgba(65, 214, 183, 0.08);
}

.upload-file-selected span {
  overflow: hidden;
  color: #f4f7fb;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.upload-file-selected small,
.upload-file-empty {
  color: #7f8b9b;
  font-size: 12px;
}

.upload-dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.upload-dialog-actions :deep(.n-button) {
  min-width: 84px;
}

@media (max-width: 720px) {
  .upload-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
