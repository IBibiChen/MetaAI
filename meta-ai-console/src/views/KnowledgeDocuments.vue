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
      </div>
      <div class="upload-actions">
        <n-button secondary @click="uploadPanelVisible = true">
          上传设置
        </n-button>
        <n-upload
            :show-file-list="false"
            :custom-request="handleUpload"
        >
          <n-button type="primary">
            <template #icon>
              <n-icon>
                <UploadCloud/>
              </n-icon>
            </template>
            上传文件
          </n-button>
        </n-upload>
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
        <n-form label-placement="top">
          <n-form-item label="可见性">
            <n-select v-model:value="uploadForm.visibility" :options="visibilityOptions"/>
          </n-form-item>
          <n-form-item label="部门 ID">
            <n-input v-model:value="uploadForm.deptId" placeholder="visibility = DEPT 时填写"/>
          </n-form-item>
          <n-form-item label="用户 ID">
            <n-input v-model:value="uploadForm.userId" placeholder="visibility = USER 时填写"/>
          </n-form-item>
          <n-form-item label="文档类型">
            <n-input v-model:value="uploadForm.documentType" placeholder="为空时由后端按文件名识别"/>
          </n-form-item>
          <n-form-item>
            <n-checkbox v-model:checked="uploadForm.autoIndex">上传后自动索引</n-checkbox>
          </n-form-item>
        </n-form>
        <template #footer>
          <n-button @click="uploadPanelVisible = false">关闭</n-button>
        </template>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script setup lang="ts">
import {computed, h, onMounted, reactive, ref} from 'vue'
import {
  NButton,
  NIcon,
  NTag,
  NSpace,
  NTooltip,
  type DataTableColumns,
  type UploadCustomRequestOptions,
  useMessage,
} from 'naive-ui'
import {Download, Play, Trash2, UploadCloud} from 'lucide-vue-next'

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

const query = reactive({
  keyword: '',
  indexStatus: undefined as string | undefined,
  visibility: undefined as string | undefined,
  current: 1,
  size: 20,
})

const uploadForm = reactive({
  visibility: 'PUBLIC',
  deptId: '',
  userId: '',
  documentType: '',
  autoIndex: false,
})

const indexStatusOptions = [
  {label: '未索引', value: 'PENDING'},
  {label: '索引中', value: 'INDEXING'},
  {label: '已索引', value: 'INDEXED'},
  {label: '失败', value: 'FAILED'},
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
  pageSizes: [10, 20, 50, 100],
}))

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
    render: (row) => row.documentType || '-',
  },
  {
    title: '大小',
    key: 'fileSize',
    width: 110,
    render: (row) => formatBytes(row.fileSize),
  },
  {
    title: '可见性',
    key: 'visibility',
    width: 100,
    render: (row) => h(NTag, {size: 'small', bordered: false}, {default: () => row.visibility}),
  },
  {
    title: '索引状态',
    key: 'indexStatus',
    width: 130,
    render: (row) =>
        h(
            NTag,
            {
              size: 'small',
              color: statusColor(row.indexStatus),
            },
            {default: () => row.indexStatus},
        ),
  },
  {
    title: 'Chunks',
    key: 'chunkCount',
    width: 90,
  },
  {
    title: '更新时间',
    key: 'updatedAt',
    width: 180,
    render: (row) => formatDate(row.updatedAt),
  },
  {
    title: '操作',
    key: 'actions',
    width: 180,
    fixed: 'right',
    render: (row) =>
        h(NSpace, {size: 6}, () => [
          iconButton(Download, '下载', () => handleDownload(row)),
          iconButton(Play, '触发索引', () => handleIndex(row)),
          iconButton(Trash2, '后端删除接口未接入', undefined, true),
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
  try {
    const page = await fetchStorageDocuments({
      tenantId: workspace.tenantId,
      knowledgeBaseId: workspace.knowledgeBaseId,
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
 * 自定义上传处理
 *
 * <p>
 * Naive UI 的 n-upload 默认只负责选择文件
 * 后端需要 tenantId、knowledgeBaseId 等额外字段，所以这里手动调用 uploadDocument
 */
async function handleUpload(options: UploadCustomRequestOptions) {
  const file = options.file.file
  if (!file) {
    options.onError()
    return
  }

  try {
    await uploadDocument({
      tenantId: workspace.tenantId,
      knowledgeBaseId: workspace.knowledgeBaseId,
      visibility: uploadForm.visibility,
      deptId: uploadForm.deptId || undefined,
      userId: uploadForm.userId || undefined,
      documentType: uploadForm.documentType || undefined,
      autoIndex: uploadForm.autoIndex,
      file,
    })
    options.onFinish()
    message.success('上传成功')
    query.current = 1
    await loadDocuments()
  } catch (error) {
    options.onError()
    message.error(error instanceof Error ? error.message : '上传失败')
  }
}

/**
 * 下载文件
 *
 * <p>
 * documentId 来自文件行数据
 * tenantId 和 knowledgeBaseId 来自顶部工作区上下文
 */
async function handleDownload(row: StorageDocument) {
  try {
    await downloadDocument(workspace.tenantId, workspace.knowledgeBaseId, row)
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
    await indexDocument(workspace.tenantId, workspace.knowledgeBaseId, row.documentId)
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
 * 构造表格操作区的图标按钮
 *
 * @example
 * iconButton(Download, '下载', () => handleDownload(row))
 */
function iconButton(
    icon: typeof Download,
    tooltip: string,
    onClick?: () => void,
    disabled = false,
) {
  return h(
      NTooltip,
      null,
      {
        trigger: () =>
            h(
                NButton,
                {
                  size: 'small',
                  quaternary: true,
                  disabled,
                  onClick,
                },
                {
                  icon: () => h(NIcon, null, {default: () => h(icon)}),
                },
            ),
        default: () => tooltip,
      },
  )
}

/**
 * 索引状态颜色
 *
 * <p>
 * INDEXED 绿色
 * INDEXING 黄色
 * FAILED 红色
 */
function statusColor(status: string) {
  if (status === 'INDEXED') return {color: '#143c35', textColor: '#72f1d4'}
  if (status === 'FAILED') return {color: '#4a1d24', textColor: '#ff8f9c'}
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
  return value ? new Date(value).toLocaleString() : '-'
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
</style>
