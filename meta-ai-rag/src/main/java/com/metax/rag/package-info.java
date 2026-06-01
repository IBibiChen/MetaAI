/**
 * MetaAI RAG 模块
 *
 * <p>
 * 本包是 MetaAI 的 RAG 核心模块，负责按 Spring AI 官方 ETL 接口完成文档索引，并通过 Modular RAG 完成检索增强生成
 *
 * <p>
 * 1、文档进入系统
 * 用户可以通过上传文件进入 RAG 文档索引链路，也可以传入已经存在于对象存储的 bucket + objectKey
 * 上传接口适合管理后台、调试工具和前端页面
 * 对象存储 objectKey 导入适合文件已经由其他系统完成归档的场景
 * 本地文件导入适合开发调试和受控离线知识库目录
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
 * 写入新 chunk 前，MetaEtlUpsertPipeline 会按 tenantId + knowledgeBaseId + documentId 删除旧 chunk
 * 这样同一个 documentId 重复上传时不会产生重复召回数据
 * 这一步依赖写入 metadata 与 Redis / Qdrant / Milvus 过滤字段保持同名
 *
 * <p>
 * 9、VectorStore 写入阶段
 * VectorStoreRouter 按 provider + vectorStore 选择目标 VectorStore Bean
 * provider 决定 EmbeddingModel，例如 dashscope、ollama、openai
 * vectorStore 决定向量数据库后端，例如 redis、qdrant、milvus
 * 不同 EmbeddingModel 的向量维度和语义空间可能不同，写入和查询必须选择同一组 provider + vectorStore
 * VectorStore 实现 Spring AI DocumentWriter 接口，写入时统一使用 write
 *
 * <p>
 * 10、RAG 检索阶段
 * RetrievalAdvisorFactory 构造 RetrievalAugmentationAdvisor
 * RetrievalAugmentationAdvisor 使用 VectorStoreDocumentRetriever 从 VectorStore 召回相似 chunk
 * topK 控制最多返回多少个 chunk
 * similarityThreshold 控制最低相似度
 * filterExpression 控制 metadata 过滤，例如 tenantId、knowledgeBaseId、documentType
 *
 * <p>
 * 11、回答与引用返回阶段
 * ChatClient 查询时会通过 RetrievalAugmentationAdvisor 把召回 chunk 注入用户问题
 * 普通 RAG 接口只返回模型回答文本，适合业务对话
 * details RAG 接口返回 answer、conversationId 和 references，适合调试召回质量和展示引用来源
 * RetrievalResponseMapper 从 ChatClientResponse 中提取 answer 和 RetrievalAugmentationAdvisor 保存的命中文档上下文
 *
 * <p>
 * Spring AI ETL 实现关系
 * <pre>{@code
 * DocumentReader      -> MetaDocumentReader
 * DocumentTransformer -> MetaDocumentMetadataTransformer
 * DocumentTransformer -> TokenTextSplitter
 * DocumentTransformer -> MetaChunkMetadataTransformer
 * DocumentTransformer -> ContentFormatTransformer
 * DocumentWriter      -> VectorStore
 * }</pre>
 *
 * <p>
 * 写入示例
 * <pre>{@code
 * curl -X POST http://localhost:8008/v1/rag/documents/upload
 *   -F provider=dashscope
 *   -F vectorStore=redis
 *   -F tenantId=t1
 *   -F knowledgeBaseId=kb1
 *   -F documentId=doc-001
 *   -F file=@spring-ai-rag.md
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
