import axios, {AxiosError} from 'axios'

import type {CommonResult} from '@/types/api'

/**
 * 全局 Axios 实例
 *
 * <p>
 * 开发期 baseURL = /api
 * Vite 会把 /api/v1 转发到后端 /v1
 *
 * @example
 * request.get('/v1/storage/documents/page')
 */
export const request = axios.create({
    baseURL: '/api',
    timeout: 120_000,
})

/**
 * 统一处理 HTTP 层错误
 *
 * <p>
 * 后端 CommonResult 的业务错误由 unwrapResult 处理
 * 网络错误、超时和 500 这类错误在这里处理
 */
request.interceptors.response.use(
    (response) => response,
    (error: AxiosError<{ message?: string }>) => {
        const message = error.response?.data?.message || error.message || '请求失败'
        return Promise.reject(new Error(message))
    },
)

/**
 * 解包后端 CommonResult<T>
 *
 * <p>
 * 成功时返回 data
 * 失败时抛出 Error，页面可以直接 message.error(error.message)
 *
 * @example
 * const data = unwrapResult(response.data)
 */
export function unwrapResult<T>(payload: CommonResult<T>): T {
    if (payload.code !== 200) {
        throw new Error(payload.message || '请求失败')
    }
    return payload.data
}
