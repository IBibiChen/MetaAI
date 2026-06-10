package com.metax.chat.support;

import com.metax.chat.history.MetaChatHistoryRole;
import com.metax.chat.history.MetaChatHistoryService;
import com.metax.chat.history.MetaChatHistoryType;
import com.metax.chat.session.MetaChatDO;
import com.metax.chat.session.MetaChatService;
import com.metax.chat.session.MetaChatUpsertRequest;
import com.metax.rag.retrieval.model.RetrievalDocumentReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.List;

/**
 * ChatHistoryRecorder .
 *
 * <p>
 * 统一处理会话主表创建、用户消息归档和助手消息归档
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@Component
@RequiredArgsConstructor
public class ChatHistoryRecorder {

    private final MetaChatService metaChatService;

    private final MetaChatHistoryService metaChatHistoryService;

    private final ChatScopeResolver chatScopeResolver;

    /**
     * 获取或创建会话主表记录
     *
     * @param chatId       会话 ID
     * @param tenantId     租户 ID
     * @param userId       用户 ID
     * @param chatMode     对话模式
     * @param firstMessage 首条用户消息
     * @param kbId         知识库 ID
     * @return 会话主表记录
     */
    public MetaChatDO getOrCreate(String chatId,
                                  String tenantId,
                                  String userId,
                                  MetaChatHistoryType chatMode,
                                  String firstMessage,
                                  String kbId) {
        ChatScope scope = chatScopeResolver.resolve(chatId, tenantId, userId);
        return metaChatService.getOrCreate(new MetaChatUpsertRequest(scope.tenantId(), scope.userId(), chatId,
                chatMode, firstMessage, kbId, null, null, "console"));
    }

    /**
     * 保存用户消息并同步会话主表最后消息
     *
     * @param chat        会话主表记录，提供历史表 fkId 和业务 chatId
     * @param historyType 历史类型
     * @param msg         用户消息
     */
    public void saveUserMessage(MetaChatDO chat, MetaChatHistoryType historyType, String msg) {
        MetaChatDO resolvedChat = requirePersistedChat(chat);
        // 完整历史先落 meta_chat_history，随后同步 meta_chat 会话列表的最后消息预览
        metaChatHistoryService.saveUserMessage(resolvedChat, historyType, msg);
        metaChatService.updateLastMessage(resolvedChat.getId(), MetaChatHistoryRole.USER, msg);
    }

    /**
     * 保存助手消息并同步会话主表最后消息
     *
     * @param chat        会话主表记录，提供历史表 fkId 和业务 chatId
     * @param historyType 历史类型
     * @param answer      助手回答
     */
    public void saveAssistantMessage(MetaChatDO chat, MetaChatHistoryType historyType, String answer) {
        MetaChatDO resolvedChat = requirePersistedChat(chat);
        // 无知识库引用的普通回答只保存 answer，不写空 references JSON
        metaChatHistoryService.saveAssistantMessage(resolvedChat, historyType, answer);
        metaChatService.updateLastMessage(resolvedChat.getId(), MetaChatHistoryRole.ASSISTANT, answer);
    }

    /**
     * 保存带引用来源的助手消息并同步会话主表最后消息
     *
     * @param chat        会话主表记录，提供历史表 fkId 和业务 chatId
     * @param historyType 历史类型
     * @param answer      助手回答
     * @param references  引用来源
     */
    public void saveAssistantMessage(MetaChatDO chat,
                                     MetaChatHistoryType historyType,
                                     String answer,
                                     List<RetrievalDocumentReference> references) {
        MetaChatDO resolvedChat = requirePersistedChat(chat);
        // 助手消息在普通响应完成后或 SSE done 阶段统一保存，避免流式 delta 片段进入历史
        metaChatHistoryService.saveAssistantMessage(resolvedChat, historyType, answer, references);
        metaChatService.updateLastMessage(resolvedChat.getId(), MetaChatHistoryRole.ASSISTANT, answer);
    }

    /**
     * 校验历史归档需要的会话主表上下文
     *
     * @param chat 会话主表记录
     * @return 已持久化且带业务 chatId 的会话主表记录
     */
    private MetaChatDO requirePersistedChat(MetaChatDO chat) {
        Assert.notNull(chat, "MetaChatDO must not be null");
        Assert.notNull(chat.getId(), "MetaChatDO id must not be null");
        Assert.hasText(chat.getChatId(), "MetaChatDO chatId must not be blank");
        return chat;
    }
}
