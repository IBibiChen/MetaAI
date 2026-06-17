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

```ts
server: {
    proxy: {
        '/api'
    :
        {
            target: 'http://localhost:8008'
        }
    }
}
```

## 实时语音输入

聊天输入区的麦克风按钮通过 FunASR Runtime WebSocket service 进行实时语音转文字

本地默认连接：

```text
ws://localhost:10096
```

如需覆盖地址，在前端环境变量中配置：

```env
VITE_FUNASR_WS_URL=ws://localhost:10096
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
