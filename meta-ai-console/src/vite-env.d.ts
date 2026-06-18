/// <reference types="vite/client" />

interface ImportMetaEnv {
    /**
     * 页面基路径
     *
     * <p>
     * 控制 Vite 静态资源路径和 Vue Router history base
     * 这是构建时变量，生产环境默认为 /meta-ai/
     */
    readonly VITE_APP_BASE_PATH?: string
    /**
     * API 网关前缀
     *
     * <p>
     * 控制 axios、SSE 和文件下载请求的统一后端入口
     * 这是构建时变量，生产环境默认为 /api/meta-ai
     */
    readonly VITE_API_BASE_URL?: string
    /**
     * ASR WebSocket 地址
     *
     * <p>
     * 用于语音输入实时转写，默认连接本地 FunASR Runtime
     * 这是构建时变量，修改后必须重新构建前端
     */
    readonly VITE_ASR_WS_URL?: string
    /**
     * 开发期 API Token
     *
     * <p>
     * 用于本地开发轻量鉴权，会进入前端构建产物，不能作为生产安全凭证
     */
    readonly VITE_METAX_API_TOKEN: string
}

interface ImportMeta {
    readonly env: ImportMetaEnv
}
