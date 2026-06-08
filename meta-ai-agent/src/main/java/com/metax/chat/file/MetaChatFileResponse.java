package com.metax.chat.file;

import com.metax.rag.retrieval.MetaContextFile;

import java.util.List;

/**
 * MetaChatFileResponse .
 *
 * @param answer         模型回答
 * @param chatId 会话 ID
 * @param files          本次参与文件对话的文件
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
public record MetaChatFileResponse(
        String answer,
        String chatId,
        List<MetaContextFile> files
) {
}
