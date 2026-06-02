/**
 * RAG 检索增强流程包
 *
 * <p>
 * 本包负责 Spring AI Modular RAG 的查询侧链路
 * 文档索引阶段把原始文件写入 VectorStore，检索阶段则按用户问题从 VectorStore 召回 chunk 并注入 prompt
 *
 * <p>
 * 检索链路顺序
 * 1、Controller 接收 provider、vectorStore、memory、tenantId、knowledgeBaseId 和用户问题
 * 2、Controller 创建 RetrievalOptions，保存本次请求的过滤字段、召回参数和原始 query
 * 3、RetrievalFilterExpressionFactory 根据 tenantId、knowledgeBaseId、documentId、documentType 生成 metadata filter
 * 4、details 调试入口传入原始 filterExpression 时，Controller 会把表达式透传到 advisor context
 * 5、RetrievalAdvisorFactory 组装 Spring AI RetrievalAugmentationAdvisor
 * 6、QueryTransformer 在检索前改写 query，none 模式会跳过这一步
 * 7、VectorStoreDocumentRetriever 使用改写后的 query 到 VectorStore 召回相似 chunk
 * 8、DocumentPostProcessor 对召回结果做 rerank 预留、去重、上下文数量限制和上下文长度限制
 * 9、ContextualQueryAugmenter 把最终文档上下文注入用户问题
 * 10、ChatClient 把增强后的 prompt 发送给当前 provider 对应的 ChatModel
 * 11、RetrievalResponseAssembler 从 ChatClientResponse 组装 answer、references 和 trace
 *
 * <p>
 * query transformer 阶段说明
 * none 表示直接使用用户原始 query 做向量检索，适合简单单轮问题和排查召回质量
 * compression 表示把多轮会话里的省略追问压缩为独立检索 query，适合类似“上面第二点呢”这种追问
 * rewrite 表示把口语化或歧义 query 改写为更适合向量检索的 query，适合单轮问题优化
 * compression 和 rewrite 都会额外调用一次当前 provider 对应的 ChatModel，因此生产环境需要按场景开启
 * query 改写可能偏离用户原意，details 响应中的 transformedQuery 是排查召回失败的第一观察点
 *
 * <p>
 * 普通 RAG 与 details RAG 的区别
 * 普通 RAG 只返回模型回答文本，适合业务对话接口
 * details RAG 返回 answer、references 和 trace，适合排查 metadata filter、topK、相似度阈值和 query 改写效果
 * raw filterExpression 只建议用于 details 调试入口，普通业务接口优先使用结构化过滤字段
 * references 来自 Spring AI RetrievalAugmentationAdvisor 保存的最终上下文 Document
 * trace 只用于调试响应，不参与 prompt 构造，也不会改变模型输入
 *
 * <p>
 * Spring AI Modular RAG 映射关系
 * <pre>{@code
 * Pre-Retrieval      -> CompressionQueryTransformer / RewriteQueryTransformer
 * Retrieval          -> VectorStoreDocumentRetriever
 * Post-Retrieval     -> DefaultDocumentPostProcessor
 * Generation Augment -> ContextualQueryAugmenter
 * Advisor            -> RetrievalAugmentationAdvisor
 * }</pre>
 *
 * <p>
 * details trace 排查顺序建议
 * 1、先看 transformedQuery 是否偏离用户原意
 * 2、再看 filter 是否包含正确 tenantId 和 knowledgeBaseId
 * 3、再看 retrievedCount 判断向量库是否召回到候选 chunk
 * 4、最后看 usedCount 判断后处理是否过滤过严
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/2
 */
package com.metax.rag.retrieval;
