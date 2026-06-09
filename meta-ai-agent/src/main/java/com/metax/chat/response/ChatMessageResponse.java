package com.metax.chat.response;

import com.metax.rag.retrieval.advisor.MetaContextFile;

import java.util.List;

/**
 * ChatMessageResponse .
 *
 * <p>
 * 普通聊天响应载体，统一承载回答、会话 ID 和本轮文件上下文来源
 * files 表示本轮实际参与上下文增强的会话文件，便于前端展示和排查 fileIds 生效情况
 *
 * @param answer 模型回答
 * @param chatId 会话 ID
 * @param files  本次参与上下文增强的会话文件
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/9
 */
public record ChatMessageResponse(
        String answer,
        String chatId,
        List<MetaContextFile> files
) {
}
