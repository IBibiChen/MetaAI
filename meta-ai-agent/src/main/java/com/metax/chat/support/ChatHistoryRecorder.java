package com.metax.chat.support;

import com.metax.chat.MetaChatDO;
import com.metax.chat.MetaChatService;
import com.metax.chat.MetaChatUpsertRequest;
import com.metax.chat.history.MetaChatHistoryRole;
import com.metax.chat.history.MetaChatHistoryService;
import com.metax.chat.history.MetaChatHistoryType;
import com.metax.rag.retrieval.model.RetrievalDocumentReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
     * @param fkId        会话主表 ID
     * @param chatId      会话 ID
     * @param historyType 历史类型
     * @param msg         用户消息
     */
    public void saveUserMessage(Long fkId, String chatId, MetaChatHistoryType historyType, String msg) {
        metaChatHistoryService.saveUserMessage(fkId, chatId, historyType, msg);
        metaChatService.updateLastMessage(fkId, MetaChatHistoryRole.USER, msg);
    }

    /**
     * 保存助手消息并同步会话主表最后消息
     *
     * @param fkId        会话主表 ID
     * @param chatId      会话 ID
     * @param historyType 历史类型
     * @param answer      助手回答
     */
    public void saveAssistantMessage(Long fkId, String chatId, MetaChatHistoryType historyType, String answer) {
        metaChatHistoryService.saveAssistantMessage(fkId, chatId, historyType, answer);
        metaChatService.updateLastMessage(fkId, MetaChatHistoryRole.ASSISTANT, answer);
    }

    /**
     * 保存带引用来源的助手消息并同步会话主表最后消息
     *
     * @param fkId        会话主表 ID
     * @param chatId      会话 ID
     * @param historyType 历史类型
     * @param answer      助手回答
     * @param references  引用来源
     */
    public void saveAssistantMessage(Long fkId,
                                     String chatId,
                                     MetaChatHistoryType historyType,
                                     String answer,
                                     List<RetrievalDocumentReference> references) {
        metaChatHistoryService.saveAssistantMessage(fkId, chatId, historyType, answer, references);
        metaChatService.updateLastMessage(fkId, MetaChatHistoryRole.ASSISTANT, answer);
    }
}
