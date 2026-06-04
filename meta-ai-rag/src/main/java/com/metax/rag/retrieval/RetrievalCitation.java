package com.metax.rag.retrieval;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * RetrievalCitation .
 *
 * <p>
 * 普通 RAG 对话引用来源，只保留前端聊天窗口需要展示和下载的字段
 *
 * <p>
 * filename 用于前端展示引用文件名
 * documentId 用于点击文件名后调用业务下载接口
 * chunk 文本、score 和 metadata 属于排查字段，只在 RetrievalReference 中保留
 *
 * @param filename   原始文件名
 * @param documentId 文档 ID
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/4
 */
@Schema(description = "RAG 对话文件引用")
public record RetrievalCitation(
        /**
         * 原始文件名
         */
        @Schema(description = "原始文件名", example = "demo.docx")
        String filename,
        /**
         * 文档 ID
         */
        @Schema(description = "文档 ID", example = "1938200000000000001")
        String documentId
) {
}
