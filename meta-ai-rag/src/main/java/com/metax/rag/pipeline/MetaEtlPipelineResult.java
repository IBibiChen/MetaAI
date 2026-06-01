package com.metax.rag.pipeline;

/**
 * MetaEtlPipelineResult .
 *
 * <p>
 * ETL Pipeline 执行结果，记录本次 upsert 最终写入的 chunk 数量
 *
 * @param chunkCount 写入 chunk 数量
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/1
 */
public record MetaEtlPipelineResult(int chunkCount) {
}
