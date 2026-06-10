/**
 * package-info .
 *
 * <p>
 * com.metax.retrieval 承载知识库问答、检索调试和会话文件增强 RAG 能力
 *
 * <p>
 * 第一阶段：接口入口和协议分流
 *
 * <p>
 * KnowledgeChatController 暴露 GET /v1/rag 和 POST /v1/rag
 * 两个入口都使用 RetrievalChatRequest 承载 chatId、tenantId、userId、kbId、msg、检索收窄条件、stream 和 fileIds
 * stream 为 true 时返回 text/event-stream，stream 为空或 false 时返回普通 JSON
 * GET 适合基础检索参数和 EventSource，POST JSON 适合复杂检索范围、fileIds 和 Authorization Header
 *
 * <p>
 * 第二阶段：检索参数和会话作用域
 *
 * <p>
 * RetrievalOptionsFactory 把 HTTP 请求转换为 RetrievalOptions
 * tenantId 和 kbId 是知识库检索硬边界，缺失时禁止进入向量库查询
 * documentId、documentType、userId 和 deptIds 是可选收窄条件
 * KnowledgeChatService 先固定 chatId，再创建或读取业务会话，并保存本轮用户消息
 * ChatMemory 负责模型上下文窗口，MetaChatHistory 负责完整历史归档
 *
 * <p>
 * 第三阶段：会话文件增强
 *
 * <p>
 * RAG 文件对话复用 com.metax.chat 的会话文件能力
 * 文件必须先通过 POST /v1/chat/files 上传并写入 scope = session 的临时向量索引
 * /v1/rag 问答阶段只传 fileIds，不再接收 MultipartFile
 * fileIds 为空表示本轮不使用会话文件，非空表示只使用用户显式选择的文件
 * 会话文件受 tenantId、userId 和 chatId 隔离，不会进入知识库 references
 *
 * <p>
 * 第四阶段：检索决策
 *
 * <p>
 * RetrievalDecisionService 会先判断本轮问题是否需要知识库检索
 * 决策为 SKIP 时不注入知识库 Advisor，避免无意义召回和知识库上下文污染
 * SKIP 场景仍然保留会话文件 Advisor，支持只基于上传文件回答
 * 决策为 RETRIEVE 时同时注入会话文件 Advisor 和知识库检索 Advisor
 *
 * <p>
 * 第五阶段：知识库检索和 Advisor 组装
 *
 * <p>
 * RetrievalAdvisorFactory 根据 VectorStore、ChatModel、RetrievalOptions 和过滤表达式创建知识库检索 Advisor
 * RetrievalFilterExpressionFactory 负责把租户、知识库、文档、用户和部门权限转换成向量库过滤表达式
 * 会话文件 Advisor 检索 scope = session 的临时文件 chunk
 * 知识库 Advisor 检索知识库文档 chunk
 * 两类上下文可以同时进入 prompt，但响应阶段分别组装为 files 和 references
 *
 * <p>
 * 第六阶段：非流式 RAG 响应
 *
 * <p>
 * 非流式链路通过 ragChatClient.prompt 组装 Advisor 后调用 call().chatClientResponse()
 * RetrievalResponseAssembler 从 ChatClientResponse 中提取模型回答、知识库 references 和会话 files
 * references 表示知识库命中文档来源，files 表示本轮参与上下文增强的会话文件
 * 助手完整回答和 references 会在模型调用完成后写入 MetaChatHistory
 *
 * <p>
 * 第七阶段：流式 RAG 响应
 *
 * <p>
 * stream=true 时 KnowledgeChatController 返回 ResponseEntity，并显式设置 Content-Type 为 text/event-stream
 * ChatStreamEventAssembler 使用 stream().chatClientResponse() 而不是 stream().content()
 * 深层响应流保留最后一次 ChatClientResponse，便于 done 阶段读取 Advisor 写入的 references 和 files
 * ChatStreamEventAssembler 统一输出 meta、delta、done 和 error 四类 SSE 事件
 * meta 事件先返回 chatId，便于前端立即绑定当前会话
 * delta 事件返回模型增量文本，前端用于实时追加展示
 * done 事件返回完整 answer、chatId、references 和 files，并在此阶段保存助手完整消息
 * error 事件返回统一错误消息，避免异常直接中断前端事件处理
 * includeReferences 只在 RETRIEVE 决策下开启，SKIP 场景不会返回知识库 references
 *
 * <p>
 * 第八阶段：知识库文档管理和检索调试
 *
 * <p>
 * 知识库文档上传、下载、列表和手动索引由 com.metax.storage 包统一承载
 * 文档必须先通过对象存储文档接口归档并保存元数据，再由 StorageDocumentService 提交核心索引服务
 * 实际 Reader、Transformer、Writer 和 VectorStore 写入仍由 meta-ai-rag 模块完成
 * RetrievalDebugController 用于查看检索明细，支持验证 topK、threshold、过滤条件和命中文档
 * 索引链路写入知识库 scope，聊天文件链路写入 session scope，两者通过 metadata 边界隔离
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/9
 */
package com.metax.retrieval;
