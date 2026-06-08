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

前端请求统一写成：

```text
/api/v1/xxx
```

Vite 会代理到：

```text
http://localhost:8008/v1/xxx
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

## 工作区上下文

顶部输入框维护这几个字段：

```text
tenantId
knowledgeBaseId
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
tenantId:userId:sessionId
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
DELETE /v1/storage/documents/{documentId}?tenantId=t1&knowledgeBaseId=kb1
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
