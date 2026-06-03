package com.metax.rag.retrieval;

import com.metax.rag.model.MetadataKeys;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * RetrievalReference .
 *
 * <p>
 * RAG 回答引用来源，来自 RetrievalAugmentationAdvisor 写入的命中文档上下文
 *
 * <p>
 * 字段说明：reference 不是模型回答的一部分，而是检索阶段命中的原始 chunk
 * text 是 chunk 文本
 * score 是向量库返回的相似度分数
 * metadata 用于展示来源、定位 documentId / chunkId，并支持前端跳转原文
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
@Schema(description = "RAG 引用来源")
public record RetrievalReference(
        /**
         * 命中 chunk 文本
         */
        @Schema(description = "命中 chunk 文本", example = "Spring AI ETL 由 Reader、Transformer、Writer 组成")
        String text,
        /**
         * 向量库返回的相似度分数
         */
        @Schema(description = "向量库返回的相似度分数", example = "0.82")
        Double score,
        /**
         * chunk metadata，用于展示来源和定位原始文档
         *
         * <p>
         * metadata.filename 是原始文件名，用于前端展示引用来源
         * metadata.source 是对象存储 objectKey，用于定位原始文件
         */
        @Schema(description = "chunk metadata，filename 为原始文件名，source 为对象存储 objectKey")
        Map<String, Object> metadata,
        /**
         * 下载地址，当前直接返回 objectKey
         */
        @Schema(description = "下载地址，当前直接返回对象存储 objectKey",
                example = "storage/t1/kb1/2026/06/2062045483370516481/demo.docx")
        String downloadUrl
) {

    public RetrievalReference(String text, Double score, Map<String, Object> metadata) {
        this(text, score, metadata, downloadUrl(metadata));
    }

    private static String downloadUrl(Map<String, Object> metadata) {
        if (metadata == null || metadata.get(MetadataKeys.SOURCE) == null) {
            return null;
        }
        return String.valueOf(metadata.get(MetadataKeys.SOURCE));
    }
}
