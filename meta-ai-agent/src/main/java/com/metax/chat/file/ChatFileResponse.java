package com.metax.chat.file;

import com.metax.rag.retrieval.MetaContextFile;

import java.util.List;

/**
 * ChatFileResponse .
 *
 * @param answer         模型回答
 * @param conversationId 会话 ID
 * @param files          本次参与文件对话的文件
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
public record ChatFileResponse(
        String answer,
        String conversationId,
        List<MetaContextFile> files
) {
}
