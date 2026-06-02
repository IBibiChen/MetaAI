/**
 * ETL Pipeline 编排包
 *
 * <p>
 * 本包负责按 Spring AI ETL 顺序编排文档索引链路
 * Reader、Transformer、Writer 各自保持单一职责，Pipeline 负责异步状态流转、执行计划组装和幂等覆盖
 *
 * <p>
 * 设计参考 DashScope upsertPipeline 的结构
 * 先把 dataSource、transformations、dataSink 和 upsert policy 组装成 pipeline，再统一执行
 * 当前项目是本地 Spring AI ETL，不照搬 DashScope 远程 request schema
 * upsert 命名只保留在 MetaVectorStoreSink，表示最终 delete + write 写入策略
 * 上层调度和执行计划使用 runIndexing / execute，避免多个层次复用同一个动作名
 *
 * <p>
 * 标准链路顺序
 * 1、MetaDocumentResourceFactory 创建 Resource 并确定 documentType
 * 2、MetaDocumentReader 委托官方 DocumentReader 解析 Document
 * 3、MetaDocumentMetadataTransformer 补齐文档级 metadata
 * 4、TokenTextSplitter 切分 chunk
 * 5、MetaChunkMetadataTransformer 补齐 chunk 级 metadata
 * 6、ContentFormatTransformer 设置内容格式化规则
 * 7、VectorStore 删除旧 chunk 并写入新 chunk
 *
 * <p>
 * 本地 pipeline 映射关系
 * dataSource -> MetaDocumentResource + DocumentReader
 * transformations -> documentMetadata + splitter + chunkMetadata + contentFormat
 * dataSink -> VectorStore / DocumentWriter
 * upsert policy -> deleteFilter + write
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
package com.metax.rag.pipeline;
