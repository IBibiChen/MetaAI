/**
 * package-info .
 *
 * <p>
 * com.metax.chat 承载普通记忆对话、会话文件对话、会话列表和完整聊天历史能力
 *
 * <p>
 * 第一阶段：接口入口和协议分流
 *
 * <p>
 * ChatMessageController 暴露 GET /v1/chat 和 POST /v1/chat
 * 两个入口都使用 ChatRequest 承载 chatId、tenantId、userId、msg、stream 和 fileIds
 * stream 为 true 时返回 text/event-stream，stream 为空或 false 时返回普通 JSON
 * GET 适合 query 参数和浏览器原生 EventSource，POST JSON 适合复杂参数、fileIds 和 Authorization Header
 *
 * <p>
 * 第二阶段：会话作用域和历史边界
 *
 * <p>
 * ChatMessageService 先通过 ChatScopeResolver 固定 chatId，并校验 tenantId、userId 和 chatId 的会话边界
 * ChatHistoryRecorder 负责维护业务会话主表和完整消息历史
 * Spring AI ChatMemory 只负责模型上下文窗口，完整历史回放不依赖 ChatMemory
 * 普通对话历史类型为 CHAT，使用会话文件时历史类型为 FILE_CHAT
 *
 * <p>
 * 第三阶段：文件对话两阶段链路
 *
 * <p>
 * MetaChatFileController 的 POST /v1/chat/files 是唯一保留 multipart/form-data 的聊天文件入口
 * 上传文件后 MetaChatFileServiceImpl 同步完成对象存储归档和元数据落库，索引由事务事件异步触发
 * 会话文件写入 scope = session 的临时向量索引，不进入知识库文档表
 * 问答接口不再接收 MultipartFile，只通过 fileIds 引用已经 READY 的会话文件
 * fileIds 为空表示本轮不使用会话文件
 * fileIds 非空表示只使用用户本轮显式选择的文件，任何不可用或越权文件都会失败
 *
 * <p>
 * 第四阶段：文件上下文增强
 *
 * <p>
 * ContextFileChatSupport 是问答链路唯一的 fileIds 解析入口
 * 它把 HTTP 层 fileIds 解析成 MetaContextFile，并向 AdvisorSpec 写入 ChatMemory conversationId 和文件检索参数
 * MetaContextFileAdvisor 只读取 CONTEXT_FILES、ORIGINAL_USER_QUERY、tenantId、userId 和 chatId
 * Advisor 不理解 HTTP 请求结构，也不主动决定本轮应该使用哪些文件
 * 文件检索必须同时限定 scope、tenantId、userId、chatId 和 fileId，防止跨租户、跨用户或跨会话召回
 *
 * <p>
 * 第五阶段：模型调用和非流式响应
 *
 * <p>
 * 无文件时 ChatClient 只绑定 ChatMemory.CONVERSATION_ID 并直接调用模型
 * 有文件时先注入 MetaContextFileAdvisor，再由 Advisor 检索 session scope 文件 chunk 并增强 prompt
 * 非流式响应返回 answer、chatId 和本轮实际参与上下文增强的 files
 * 助手完整回答会在模型调用完成后写入 MetaChatHistory
 *
 * <p>
 * 第六阶段：流式返回
 *
 * <p>
 * stream=true 时 ChatMessageController 返回 ResponseEntity，并显式设置 Content-Type 为 text/event-stream
 * ChatStreamEventAssembler 统一输出 meta、delta、done 和 error 四类 SSE 事件
 * meta 事件先返回 chatId，便于前端立即绑定当前会话
 * delta 事件返回模型增量文本，前端用于实时追加展示
 * done 事件返回完整 answer、chatId、references 和 files，并在此阶段保存助手完整消息
 * error 事件返回统一错误消息，避免异常直接中断前端事件处理
 * 文件流式对话使用 chatClientResponseStream，因为 done 阶段需要读取 Advisor 写入的 files 元数据
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/9
 */
package com.metax.chat;
