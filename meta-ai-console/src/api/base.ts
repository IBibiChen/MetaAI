/**
 * 前端 API 地址配置
 *
 * <p>
 * 这里只处理后端 API 请求前缀，不处理页面路由和静态资源路径
 * axios、EventSource、fetchEventSource 和裸 fetch 下载都必须复用这里，避免网关前缀散落在业务代码中
 */

/**
 * 开发期 API 默认前缀
 *
 * <p>
 * Vite dev server 会把 /api/v1 转发到 Spring Boot 后端 /v1
 */
const DEFAULT_API_BASE_URL = '/api'

/**
 * 生产 ASR 默认网关路径
 *
 * <p>
 * WebSocket 地址运行时基于当前页面 origin 推导，避免本地单机部署时把服务器地址写死进构建产物
 */
const DEFAULT_ASR_WS_PATH = '/asr/ws'

/**
 * 规范化 API 基路径
 *
 * <p>
 * 开发期默认走 Vite /api 代理，生产期由 VITE_API_BASE_URL 指向 Nginx 网关前缀
 * 空值或 / 表示前端与后端同源根路径部署，尾部 / 会被移除，避免拼接出双斜杠
 */
function normalizeApiBaseUrl(value?: string) {
  if (!value || value === '/') {
    return ''
  }
  return value.endsWith('/') ? value.slice(0, -1) : value
}

/**
 * 当前构建产物使用的 API 基路径
 *
 * <p>
 * VITE_API_BASE_URL 是 Vite 构建时变量，修改后必须重新构建前端才会生效
 */
export const API_BASE_URL = normalizeApiBaseUrl(
  import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL,
)

/**
 * 拼接 API URL
 *
 * <p>
 * path 必须使用应用内部接口路径，例如 /v1/chat
 * 外部网关前缀只允许通过 VITE_API_BASE_URL 注入
 *
 * @param path 应用内部接口路径
 * @return 带网关前缀的请求 URL
 */
export function apiUrl(path: string) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${API_BASE_URL}${normalizedPath}`
}

/**
 * 解析 ASR WebSocket 地址
 *
 * <p>
 * VITE_ASR_WS_URL 支持显式 ws:// / wss:// 地址，也支持 /asr/ws 这类同源网关路径
 * 同源路径会根据当前页面协议自动转换为 ws 或 wss
 *
 * @return 可直接传给 WebSocket 构造器的地址
 */
export function resolveAsrWebSocketUrl() {
  const configuredUrl = import.meta.env.VITE_ASR_WS_URL?.trim()
  if (configuredUrl?.startsWith('ws://') || configuredUrl?.startsWith('wss://')) {
    return configuredUrl
  }

  const configuredPath = configuredUrl || DEFAULT_ASR_WS_PATH
  const normalizedPath = configuredPath.startsWith('/') ? configuredPath : `/${configuredPath}`
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}${normalizedPath}`
}
