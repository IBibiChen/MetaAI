import {request, unwrapResult} from './request'

import type {
    CommonResult,
    PageResult,
    StorageDocument,
    StorageDocumentQuery,
    StorageDocumentUploadResponse,
} from '@/types/api'

/**
 * 分页查询知识库文件
 *
 * @example
 * fetchStorageDocuments({
 *   tenantId: 't1',
 *   kbId: 'kb1',
 *   current: 1,
 *   size: 20
 * })
 */
export async function fetchStorageDocuments(params: StorageDocumentQuery) {
    const response = await request.get<CommonResult<PageResult<StorageDocument>>>('/v1/storage/documents/page', {
        params,
    })
    return unwrapResult(response.data)
}

/**
 * 上传文件参数
 *
 * <p>
 * file 必须是真实 File 对象
 * 其余字段会被转换为 multipart/form-data 字段
 */
export interface UploadDocumentPayload {
    /** 租户 ID */
    tenantId: string
    /** 知识库 ID */
    kbId: string
    /** 文档可见性 */
    visibility: string
    /** 部门 ID，visibility = DEPT 时填写 */
    deptId?: string
    /** 用户 ID，visibility = USER 时填写 */
    userId?: string
    /** 文档类型，留空时后端按文件名识别 */
    documentType?: string
    /** 是否上传后自动触发索引 */
    autoIndex: boolean
    /** 浏览器选择的文件对象 */
    file: File
}

/**
 * 上传知识库文件
 *
 * <p>
 * 后端接口要求 multipart/form-data
 *
 * @example
 * uploadDocument({
 *   tenantId: 't1',
 *   kbId: 'kb1',
 *   visibility: 'PUBLIC',
 *   autoIndex: false,
 *   file
 * })
 */
export async function uploadDocument(payload: UploadDocumentPayload) {
    const formData = new FormData()
    formData.append('tenantId', payload.tenantId)
    formData.append('kbId', payload.kbId)
    formData.append('visibility', payload.visibility)
    formData.append('autoIndex', String(payload.autoIndex))
    formData.append('file', payload.file)

    if (payload.deptId) formData.append('deptId', payload.deptId)
    if (payload.userId) formData.append('userId', payload.userId)
    if (payload.documentType) formData.append('documentType', payload.documentType)

    const response = await request.post<CommonResult<StorageDocumentUploadResponse>>(
        '/v1/storage/documents/upload',
        formData,
    )
    return unwrapResult(response.data)
}

/**
 * 手动触发文档索引
 *
 * <p>
 * 只提交异步索引任务
 * 最新状态仍然需要重新查询文件分页接口
 */
export async function indexDocument(tenantId: string, kbId: string, documentId: string) {
    const response = await request.post<CommonResult<StorageDocument>>(
        `/v1/storage/documents/${encodeURIComponent(documentId)}/index`,
        undefined,
        {
            params: {
                tenantId,
                kbId,
            },
        },
    )
    return unwrapResult(response.data)
}

/**
 * 下载知识库文件
 *
 * <p>
 * 浏览器端通过 Blob 临时地址触发下载
 * 文件名优先使用 originalFilename
 */
export async function downloadDocument(tenantId: string, kbId: string, document: StorageDocument) {
    const response = await request.get<Blob>(
        `/v1/storage/documents/${encodeURIComponent(document.documentId)}/download`,
        {
            params: {
                tenantId,
                kbId,
            },
            responseType: 'blob',
        },
    )

    await assertDownloadBlob(response.data)

    const filename = filenameFromDisposition(response.headers['content-disposition']) ||
        document.originalFilename ||
        document.documentId
    const url = URL.createObjectURL(response.data)
    const link = window.document.createElement('a')
    link.href = url
    link.download = filename
    link.click()
    URL.revokeObjectURL(url)
}

async function assertDownloadBlob(blob: Blob) {
    if (!blob.size) {
        throw new Error('下载文件为空')
    }

    if (isJsonBlob(blob)) {
        const text = await blob.text()
        throw new Error(errorMessageFromJson(text) || '下载失败')
    }
}

function isJsonBlob(blob: Blob) {
    const type = blob.type.toLowerCase()
    return type.includes('application/json') || type.includes('text/json')
}

function errorMessageFromJson(text: string) {
    try {
        const payload = JSON.parse(text) as Partial<CommonResult<unknown>> & { message?: string }
        return payload.message
    } catch {
        return undefined
    }
}

function filenameFromDisposition(disposition?: string) {
    if (!disposition) return undefined

    const encodedMatch = disposition.match(/filename\*=UTF-8''([^;]+)/i)
    if (encodedMatch?.[1]) {
        return decodeDispositionValue(encodedMatch[1])
    }

    const plainMatch = disposition.match(/filename="?([^";]+)"?/i)
    if (plainMatch?.[1]) {
        return decodeDispositionValue(plainMatch[1])
    }

    return undefined
}

function decodeDispositionValue(value: string) {
    try {
        return decodeURIComponent(value)
    } catch {
        return value
    }
}
