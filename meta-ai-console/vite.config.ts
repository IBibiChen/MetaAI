import {fileURLToPath, URL} from 'node:url'

import vue from '@vitejs/plugin-vue'
import {defineConfig, loadEnv} from 'vite'

export default defineConfig(({mode}) => {
    // npm run dev 默认 mode = development，npm run build 默认 mode = production
    // 按 mode 读取对应 .env 配置，build:local 使用 .env.boot
    const env = loadEnv(mode, process.cwd(), '')
    return {
        // 页面基路径控制静态资源 URL 和 Vue Router history base，生产环境挂在 /meta-ai/
        base: env.VITE_APP_BASE_PATH || '/',
        plugins: [vue()],
        resolve: {
            alias: {
                // new URL('./src', import.meta.url) 先基于当前配置文件定位 src 目录
                // fileURLToPath 再把 file:// URL 转为 Windows / Linux 都可用的真实文件路径
                '@': fileURLToPath(new URL('./src', import.meta.url)),
            },
        },
        server: {
            port: 5173,
            proxy: {
                // 该代理只服务本地开发，生产 API 由 Nginx 暴露 /api/meta-ai
                '/api': {
                    target: 'http://localhost:8008',
                    changeOrigin: true,
                    // 开发期 /api/v1 会转成 Spring Boot 内部 /v1
                    rewrite: (path) => path.replace(/^\/api/, ''),
                },
            },
        },
    }
})
