package com.metax.history;

/**
 * MetaChatUpsertRequest .
 *
 * <p>
 * 创建或获取聊天会话所需参数
 *
 * @param tenantId        租户 ID
 * @param userId          用户 ID
 * @param conversationId  会话 ID
 * @param chatMode        会话模式
 * @param firstMessage    首条用户消息
 * @param knowledgeBaseId 知识库 ID
 * @param modelProvider   模型 provider
 * @param modelName       模型名称
 * @param source          会话来源
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/4
 */
public record MetaChatUpsertRequest(
        String tenantId,
        String userId,
        String conversationId,
        ChatHistoryType chatMode,
        String firstMessage,
        String knowledgeBaseId,
        String modelProvider,
        String modelName,
        String source
) {
}
