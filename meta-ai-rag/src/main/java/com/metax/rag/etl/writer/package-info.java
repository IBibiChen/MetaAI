/**
 * RAG ETL Writer 包
 *
 * <p>
 * 当前项目把 Spring AI DocumentWriter 分成生产索引写入和 ETL 快照写入两类
 * 生产索引写入仍然是 Redis、Qdrant、Milvus 对应的 VectorStore
 * ETL 快照写入使用 Spring AI 官方 FileDocumentWriter 导出处理后的 Document
 *
 * <p>
 * FileDocumentWriter 适合观察 Reader 和 Transformer 处理后的 Document
 * 它可以帮助排查 chunk 切分结果、metadata、ContentFormatter 和 metadataMode 是否符合预期
 * 它不具备向量库写入、检索一致性和索引成功语义，因此不能替代生产 VectorStore
 *
 * <p>
 * 典型链路
 * <pre>{@code
 * DocumentReader
 *   -> DocumentTransformer
 *   -> MetaDocumentSnapshotWriter
 *   -> VectorStore
 * }</pre>
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
package com.metax.rag.etl.writer;
