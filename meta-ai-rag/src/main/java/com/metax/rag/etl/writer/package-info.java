/**
 * DocumentWriter 说明包
 *
 * <p>
 * 当前项目不额外封装自定义 Writer，因为 Spring AI VectorStore 已经实现 DocumentWriter
 * MetaEtlPipeline 直接把 Redis、Qdrant、Milvus 对应的 VectorStore 作为写入目标
 *
 * <p>
 * 保留该包用于后续扩展批量写入审计、失败重试或多目标同步等 Writer 能力
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
package com.metax.rag.etl.writer;
