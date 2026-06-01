/**
 * MetaAI RAG 模块
 *
 * <p>
 * 本包是 MetaAI 的 RAG 核心模块，负责按 Spring AI 官方 ETL 接口完成文档索引，并通过 Modular RAG 完成检索增强生成
 *
 * <p>
 * 1、文档进入系统
 * RAG 索引链路只消费已经归档好的文件资源
 * 生产入口是对象存储 bucket + objectKey，适合管理后台或业务系统先完成原始文件归档的场景
 * 本地文件导入适合开发调试和受控离线知识库目录
 * 如果业务需要接收原始文件，应在独立文件归档模块先写入对象存储，再调用对象存储导入接口
 *
 * <p>
 * 2、原始文件存储
 * 对象存储或受控本地目录负责保存原始文件，VectorStore 只保存 chunk 文本、向量和 metadata
 * 原始文件和向量数据通过 source / objectKey 建立关联
 * 这样后续需要重新切分、重建索引、审计来源时，可以从原始文件来源重新读取原文件
 *
 * <p>
 * 3、异步文档索引任务创建
 * DocumentIndexingService 创建 DocumentIndexingJob，并把任务状态写入 Redis
 * DocumentIndexingService 不直接执行耗时 ETL，真正文档索引交给 MetaEtlPipeline
 * DocumentIndexingService 也不负责原始文件归档，它只接收已经可被 ResourceFactory 解析的资源描述
 * 这样可以避免 HTTP 请求被大文件解析、embedding 和 VectorStore 写入阻塞
 *
 * <p>
 * 4、DocumentReader 阶段
 * MetaDocumentResourceFactory 先把对象存储文件流或本地文件统一转换为 Spring Resource
 * MetaDocumentReader 实现 Spring AI DocumentReader 接口，并委托官方 Reader 解析
 * txt 使用 Spring AI TextReader
 * md / markdown 使用 Spring AI MarkdownDocumentReader
 * json 使用 Spring AI JsonReader
 * pdf / docx / html 等复杂格式使用 Spring AI TikaDocumentReader 兜底解析
 * documentType 可以显式传入，也可以根据文件名或 objectKey 后缀自动识别
 *
 * <p>
 * 5、文档级 metadata 增强阶段
 * MetaDocumentMetadataTransformer 实现 Spring AI DocumentTransformer 接口
 * 它在切分前为原始 Document 补齐租户、知识库、文档和来源字段
 * tenantId 是租户边界，检索时必须过滤
 * knowledgeBaseId 是知识库边界，检索时必须过滤
 * documentId 用于幂等覆盖和按文档收窄查询
 * documentType 用于选择解析器和按类型过滤
 * source 用于返回引用来源和定位对象存储 objectKey 或本地相对路径
 * createdAt 使用 epoch millis，便于范围过滤
 *
 * <p>
 * 6、Chunk 切分阶段
 * TokenTextSplitter 是 Spring AI 内置 DocumentTransformer
 * 它按 token 粒度切分文本，比按字符数切分更贴近模型上下文预算
 * metax.ai.rag.chunk.size 控制目标 chunk token 数，默认 800
 * metax.ai.rag.chunk.min-chars 控制最小 chunk 字符数，避免为了标点切出过短片段
 * metax.ai.rag.chunk.min-length-to-embed 控制最短索引文本，避免目录、页眉、页脚污染向量库
 * metax.ai.rag.chunk.max-num-chunks 控制单文档最大 chunk 数，避免异常大文件打爆 embedding 请求
 * metax.ai.rag.chunk.keep-separator 控制是否保留换行和分隔符，markdown、代码和列表文档建议保留
 *
 * <p>
 * 7、chunk 级 metadata 增强阶段
 * MetaChunkMetadataTransformer 实现 Spring AI DocumentTransformer 接口
 * 它在 TokenTextSplitter 之后为 chunk Document 补齐项目统一 chunk 字段
 * chunkId 使用 documentId + chunkIndex 构造
 * chunkIndex 用于还原文档片段顺序和前端定位
 * contentHash 后续可用于增量索引和审计
 * TokenTextSplitter 自带的 parent_document_id、chunk_index、total_chunks 会继续保留
 *
 * <p>
 * 8、幂等覆盖阶段
 * 写入新 chunk 前，MetaEtlUpsertPipeline 可以先通过 FileDocumentWriter 导出 ETL 快照
 * 快照用于观察最终进入 VectorStore 前的 chunk 内容、metadata 和格式化结果
 * 快照不是索引成功凭证，生产写入仍以 VectorStore 为准
 *
 * <p>
 * 9、幂等覆盖阶段
 * 写入新 chunk 前，MetaVectorStoreSink 会按 tenantId + knowledgeBaseId + documentId 删除旧 chunk
 * 这样同一个 documentId 重复上传时不会产生重复召回数据
 * 这一步依赖写入 metadata 与 Redis / Qdrant / Milvus 过滤字段保持同名
 *
 * <p>
 * 10、VectorStore 写入阶段
 * VectorStoreRouter 按 provider + vectorStore 选择目标 VectorStore Bean
 * provider 决定 EmbeddingModel，例如 dashscope、ollama、openai
 * vectorStore 决定向量数据库后端，例如 redis、qdrant、milvus
 * 不同 EmbeddingModel 的向量维度和语义空间可能不同，写入和查询必须选择同一组 provider + vectorStore
 * VectorStore 实现 Spring AI DocumentWriter 接口，写入时统一使用 write
 *
 * <p>
 * 11、RAG 查询入口阶段
 * ChatController 接收 provider、vectorStore、memory、tenantId、knowledgeBaseId 和用户问题
 * 普通 RAG 查询必须传 tenantId 和 knowledgeBaseId，避免无过滤全库检索
 * provider 用于选择 ChatModel 和 EmbeddingModel 语义空间
 * vectorStore 用于选择 Redis、Qdrant 或 Milvus 向量库后端
 * memory 用于选择 Redis ChatMemory 或 JDBC ChatMemory
 *
 * <p>
 * 12、检索参数组装阶段
 * RetrievalOptions 保存本次请求的过滤参数、召回参数和原始 query
 * RetrievalFilterExpressionFactory 优先使用结构化字段生成 Filter.Expression
 * tenantId 和 knowledgeBaseId 是强制过滤边界
 * documentId 和 documentType 是可选收窄条件
 * 原始 filterExpression 只建议用于 details 调试场景
 *
 * <p>
 * 13、RAG Advisor 组装阶段
 * RetrievalAdvisorFactory 构造 Spring AI RetrievalAugmentationAdvisor
 * 当前项目使用官方 Modular RAG 扩展点，不手写检索增强主流程
 * QueryTransformer、VectorStoreDocumentRetriever、DocumentPostProcessor 和 ContextualQueryAugmenter 都在这里按配置组装
 * ChatModel 必须由调用侧按 provider 显式传入，避免多 ChatModel 共存时注入错误模型
 *
 * <p>
 * 14、检索前 query 转换阶段
 * QueryTransformer 是 Spring AI pre-retrieval 阶段组件
 * compression 模式使用 CompressionQueryTransformer，适合多轮追问场景
 * rewrite 模式使用 RewriteQueryTransformer，适合单轮检索优化场景
 * query 转换会额外调用一次 ChatModel，因此生产环境应按场景开启
 * query 转换使用低 temperature，避免改写结果不稳定
 *
 * <p>
 * 15、向量召回阶段
 * VectorStoreDocumentRetriever 使用 query embedding 到 VectorStore 做 similaritySearch
 * topK 控制最多返回多少个 chunk
 * similarityThreshold 控制最低相似度
 * filterExpression 控制 metadata 过滤，例如 tenantId、knowledgeBaseId、documentType
 * 写入和查询必须选择同一组 provider + vectorStore，避免 embedding 语义空间错配
 *
 * <p>
 * 16、检索后文档处理阶段
 * DocumentPostProcessor 是 Spring AI post-retrieval 阶段组件
 * DefaultDocumentPostProcessor 当前负责 rerank 扩展预留、chunk 去重、限制上下文文档数量和限制上下文总字符数
 * DocumentReranker 是项目内部 rerank 抽象，用于后续接入真实 rerank 模型
 * NoOpDocumentReranker 是当前空实现，不改变排序、不计算 rerankScore
 * 去重优先使用 chunkId，缺失时使用 contentHash 兜底
 * maxContextDocuments 控制最终进入 prompt 的 chunk 数量
 * maxContextChars 控制最终进入 prompt 的文本总长度
 * rerank-enabled 当前只会调用 NoOpDocumentReranker，后续可接 cross-encoder 或第三方 rerank 模型
 *
 * <p>
 * 17、提示词增强阶段
 * ContextualQueryAugmenter 把后处理后的 Document 上下文注入用户问题
 * allowEmptyContext=false 表示没有召回上下文时不让模型自由发挥
 * 这样可以降低 RAG 场景中脱离知识库编造答案的风险
 *
 * <p>
 * 18、回答与引用返回阶段
 * ChatClient 把增强后的 prompt 发送给 ChatModel
 * 普通 RAG 接口只返回模型回答文本，适合业务对话
 * details RAG 接口返回 answer、conversationId、references 和 trace，适合调试召回质量和展示引用来源
 * RetrievalResponseAssembler 从 ChatClientResponse 中提取 answer、命中文档上下文和 RetrievalTrace
 *
 * <p>
 * 19、检索链路 trace 阶段
 * RetrievalTrace 不参与 prompt 构造，只用于 details 响应
 * TracingQueryTransformer 记录 transformedQuery 和 queryTransform 耗时
 * TracingDocumentPostProcessor 记录 retrievedCount、usedCount、topK、similarityThreshold、filter 和 postProcess 耗时
 * trace 可以帮助判断问题出在 query 转换、metadata filter、向量召回还是上下文后处理
 *
 * <p>
 * Spring AI ETL 实现关系
 * <pre>{@code
 * DocumentReader      -> MetaDocumentReader
 * DocumentTransformer -> MetaDocumentMetadataTransformer
 * DocumentTransformer -> TokenTextSplitter
 * DocumentTransformer -> MetaChunkMetadataTransformer
 * DocumentTransformer -> ContentFormatTransformer
 * DocumentWriter      -> FileDocumentWriter
 * DocumentWriter      -> VectorStore
 * }</pre>
 *
 * <p>
 * Spring AI RAG 实现关系
 * <pre>{@code
 * QueryTransformer       -> CompressionQueryTransformer / RewriteQueryTransformer
 * DocumentRetriever      -> VectorStoreDocumentRetriever
 * DocumentPostProcessor  -> DefaultDocumentPostProcessor
 * DocumentReranker       -> NoOpDocumentReranker
 * QueryAugmenter         -> ContextualQueryAugmenter
 * Advisor                -> RetrievalAugmentationAdvisor
 * }</pre>
 *
 * <p>
 * 对象存储 objectKey 导入示例
 * <pre>{@code
 * curl -X POST http://localhost:8008/v1/rag/documents/import
 *   -d provider=dashscope
 *   -d vectorStore=redis
 *   -d tenantId=t1
 *   -d knowledgeBaseId=kb1
 *   -d documentId=doc-001
 *   -d source=knowledge/t1/kb1/demo.md
 *   -d bucket=meta-ai-knowledge
 *   -d objectKey=knowledge/t1/kb1/demo.md
 * }</pre>
 *
 * <p>
 * 本地文件导入示例
 * <pre>{@code
 * curl -X POST http://localhost:8008/v1/rag/documents/import/local
 *   -d provider=dashscope
 *   -d vectorStore=redis
 *   -d tenantId=t1
 *   -d knowledgeBaseId=kb1
 *   -d documentId=doc-001
 *   -d path=docs/demo.md
 * }</pre>
 *
 * <p>
 * 检索详情示例
 * <pre>{@code
 * curl -X POST http://localhost:8008/v1/dashscope/rag/redis/redis/details
 *   -d tenantId=t1
 *   -d knowledgeBaseId=kb1
 *   -d msg=Spring AI 的 ETL 是什么
 *   -d topK=5
 *   -d threshold=0.5
 * }</pre>
 *
 * <p>
 * query transformer 配置示例
 * <pre>{@code
 * metax.ai.rag.retrieval.query-transformer.enabled=true
 * metax.ai.rag.retrieval.query-transformer.mode=compression
 * metax.ai.rag.retrieval.query-transformer.temperature=0.0
 * metax.ai.rag.retrieval.query-transformer.max-tokens=512
 * }</pre>
 *
 * <p>
 * document post processor 配置示例
 * <pre>{@code
 * metax.ai.rag.retrieval.post-processor.enabled=true
 * metax.ai.rag.retrieval.post-processor.deduplicate-enabled=true
 * metax.ai.rag.retrieval.post-processor.max-context-documents=5
 * metax.ai.rag.retrieval.post-processor.max-context-chars=12000
 * }</pre>
 *
 * <p>
 * ETL 快照配置示例
 * <pre>{@code
 * metax.ai.rag.snapshot.enabled=true
 * metax.ai.rag.snapshot.output-dir=D:/meta-ai/rag-snapshots
 * metax.ai.rag.snapshot.with-document-markers=true
 * metax.ai.rag.snapshot.metadata-mode=ALL
 * metax.ai.rag.snapshot.append=false
 * }</pre>
 *
 * <p>
 * details trace 示例
 * <pre>{@code
 * {
 *   "query": "上面第二点是什么意思",
 *   "transformedQuery": "Spring AI RAG 文档中第二点的含义",
 *   "queryTransformerMode": "compression",
 *   "filter": "tenantId == 't1' && knowledgeBaseId == 'kb1'",
 *   "topK": 5,
 *   "similarityThreshold": 0.5,
 *   "retrievedCount": 5,
 *   "usedCount": 3
 * }
 * }</pre>
 *
 * <p>
 * 索引链路顺序示例
 * <pre>{@code
 * bucket + objectKey
 *   -> MetaDocumentResourceFactory
 *   -> MetaDocumentReader
 *   -> MetaDocumentMetadataTransformer
 *   -> TokenTextSplitter
 *   -> MetaChunkMetadataTransformer
 *   -> ContentFormatTransformer
 *   -> MetaDocumentSnapshotWriter
 *   -> VectorStore.write
 * }</pre>
 *
 * <p>
 * 检索链路顺序示例
 * <pre>{@code
 * user query
 *   -> CompressionQueryTransformer / RewriteQueryTransformer
 *   -> VectorStoreDocumentRetriever
 *   -> DefaultDocumentPostProcessor
 *   -> ContextualQueryAugmenter
 *   -> ChatModel
 * }</pre>
 *
 * <p>
 * metadata 示例
 * <pre>{@code
 * tenantId=t1
 * knowledgeBaseId=kb1
 * documentId=doc-001
 * documentType=markdown
 * source=knowledge/t1/kb1/demo.md
 * createdAt=1710000000000
 * chunkId=doc-001:0
 * chunkIndex=0
 * contentHash=md5
 * }</pre>
 *
 * <p>
 * filterExpression 示例
 * <pre>{@code
 * tenantId == 't1' && knowledgeBaseId == 'kb1'
 * tenantId == 't1' && knowledgeBaseId == 'kb1' && documentType == 'markdown'
 * }</pre>
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
package com.metax.rag;
