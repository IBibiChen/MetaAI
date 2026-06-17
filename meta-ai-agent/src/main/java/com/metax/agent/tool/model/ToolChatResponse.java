package com.metax.agent.tool.model;

import java.util.List;

/**
 * ToolChatResponse .
 *
 * <p>
 * 请求级显式工具对话响应载体，单独返回本轮允许暴露的工具名称，避免和知识库 references 混用
 *
 * @param answer    模型回答
 * @param chatId    会话 ID
 * @param toolNames 本轮允许暴露给模型的工具名称
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
public record ToolChatResponse(
        String answer,
        String chatId,
        List<String> toolNames
) {
}
