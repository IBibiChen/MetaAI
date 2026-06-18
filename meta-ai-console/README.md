# MetaAI Console

MetaAI Console 是当前仓库的前端控制台，只负责前端页面开发，不修改后端模块代码

## 技术栈

- Vue 3：页面和组件框架
- TypeScript：给接口字段、组件状态和工具方法提供类型约束
- Vite：开发服务器和构建工具
- Vue Router：页面路由
- Pinia：全局工作区上下文
- Axios：请求后端接口
- Naive UI：企业级控制台组件库

## 目录说明

```text
meta-ai-console
  src/api          后端接口封装
  src/router       页面路由
  src/stores       全局状态
  src/types        后端接口类型
  src/views        页面
  src/styles       全局样式
```

## 本地启动

先安装依赖：

```bash
npm install
```

启动前端：

```bash
npm run dev
```

默认访问：

```text
http://localhost:5173
```

## 构建模式

生产构建使用 `.env.production`，前端资源路径带 `/meta-ai/` 前缀，适合通过 Nginx 网关访问：

```bash
npm run build
```

本地 `java -jar` 直连验证使用 `.env.boot`，前端资源和 API 都从 Spring Boot 根路径访问：

```bash
npm run build:boot
```

`.env.boot` 的 API 前缀应为 `/`，这样浏览器会直接请求 `/v1/**`，不再依赖 Vite proxy 或 Nginx rewrite

如果先执行 `npm run build:boot` 生成本地直连版 `dist`，后端重新打 jar 时必须使用 `-Dmeta-ai.console.skip=true`，避免
Maven 再次执行默认生产构建覆盖 `dist`

后端本地 Maven 打包默认使用 `dev` profile，会保留 `application*.properties`，方便直接 `java -jar` 验证。Docker 镜像构建使用
`-Pdocker`，会排除这些环境配置文件，运行时必须通过外部挂载或环境变量注入

## 环境变量总览

前端只读取 `VITE_` 前缀的构建时变量，修改后必须重新构建才会进入浏览器产物：

```text
VITE_APP_BASE_PATH      页面基路径和 Vue Router history base
VITE_API_BASE_URL       后端 API 网关前缀
VITE_METAX_API_TOKEN    开发期轻量 API Token
VITE_ASR_WS_URL         ASR WebSocket 地址
```

`VITE_` 变量会进入浏览器构建产物，不能把 `VITE_METAX_API_TOKEN` 当作生产安全凭证

## 后端代理

开发期 API 前缀由 `.env.development` 配置：

```text
VITE_API_BASE_URL=/api
```

前端代码统一使用内部接口路径，例如：

```text
/v1/xxx
```

开发期 Vite 会把 `/api/v1/xxx` 代理到：

```text
http://localhost:8008/v1/xxx
```

生产期 API 前缀由 `.env.production` 配置：

```text
VITE_APP_BASE_PATH=/meta-ai/
VITE_API_BASE_URL=/api/meta-ai
```

老系统 iframe 嵌入入口：

```text
/meta-ai/embed/chat?tenantId=t1&userId=u1&kbId=kb1
```

URL query 只作为嵌入上下文入口，不作为可信身份凭证

生产网关会把 `/meta-ai/` 页面请求代理到后端应用根路径，因此 Spring Boot 实际接收的前端路由是 `/chat`、`/documents` 和
`/embed/chat`

后端只对这些明确页面路由回退到 `index.html`。不要改成全局通配 SPA fallback，否则后端 API 的 404 会被前端页面吞掉

完整 Nginx 网关示例见：

```text
docker/compose/README.md
```

代理配置在：

```text
vite.config.ts
```

如果后端端口不是 8008，修改这里：

```
server: {
    proxy: {
        '/api': {
            target: 'http://localhost:8008'
        }
    }
}
```

## 实时语音输入

聊天输入区的麦克风按钮通过 FunASR Runtime WebSocket service 进行实时语音转文字

生产构建默认使用同源网关路径：

```env
VITE_ASR_WS_URL=/asr/ws
```

前端运行时会根据当前页面地址自动推导 WebSocket 地址：

```text
http://服务器 IP:端口/meta-ai/ -> ws://服务器 IP:端口/asr/ws
https://服务器 IP:端口/meta-ai/ -> wss://服务器 IP:端口/asr/ws
```

如需绕过网关直连 ASR 服务，可在前端环境变量中配置显式地址：

```env
VITE_ASR_WS_URL=ws://localhost:10096
```

FunASR 服务镜像和离线部署文档位于：

```text
docker/FunASR
```

语音输入只把最终转写文本写入输入框，不会直接写入后端聊天历史。用户仍需点击发送按钮，复用现有普通聊天或 RAG 聊天流程

## 工作区上下文

顶部输入框维护这几个字段：

```text
tenantId
kbId
userId
deptIds
```

这些字段会同时用于：

- 知识库文件分页
- 文件上传
- 文件下载
- 手动索引
- RAG 聊天

chatId 会自动生成：

```text
tenantId-userId-sessionId
```

示例：

```text
t1:u1:9a8b7c6d
```

## 已接入接口

知识库文件：

```text
GET  /v1/storage/documents/page
POST /v1/storage/documents/upload
GET  /v1/storage/documents/{documentId}/download
POST /v1/storage/documents/{documentId}/index
```

聊天：

```text
GET  /v1/chat
GET  /v1/rag
GET  /v1/chat/history/page
```

## 删除功能说明

当前后端没有删除接口，所以前端删除按钮是禁用状态

后端补齐后，建议接入：

```text
DELETE /v1/storage/documents/{documentId}?tenantId=t1&kbId=kb1
```

## 常用命令

类型检查和生产构建：

```bash
npm run build
```

只做类型检查：

```bash
npm run typecheck
```

预览生产构建：

```bash
npm run preview
```
