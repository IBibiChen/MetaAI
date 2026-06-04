package com.metax.history;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.metax.rag.retrieval.RetrievalCitation;

import java.util.List;

/**
 * ChatHistoryService .
 *
 * <p>
 * 完整聊天历史服务
 * ChatMemory 用于模型上下文窗口，超过 maxMessages 的旧消息会被裁剪
 * ChatHistory 用于用户查看历史、分页查询和后续审计，不参与 prompt 构造
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/2
 */
public interface ChatHistoryService extends IService<ChatHistoryDO> {

    /**
     * 保存用户消息
     *
     * @param conversationId 会话 ID
     * @param type           对话类型
     * @param content        消息内容
     */
    void saveUserMessage(String conversationId, ChatHistoryType type, String content);

    /**
     * 保存用户消息
     *
     * @param chatId         会话主键
     * @param conversationId 会话 ID
     * @param type           对话类型
     * @param content        消息内容
     */
    void saveUserMessage(Long chatId, String conversationId, ChatHistoryType type, String content);

    /**
     * 保存模型回答
     *
     * @param conversationId 会话 ID
     * @param type           对话类型
     * @param content        消息内容
     */
    void saveAssistantMessage(String conversationId, ChatHistoryType type, String content);

    /**
     * 保存模型回答
     *
     * @param chatId         会话主键
     * @param conversationId 会话 ID
     * @param type           对话类型
     * @param content        消息内容
     */
    void saveAssistantMessage(Long chatId, String conversationId, ChatHistoryType type, String content);

    /**
     * 保存模型回答
     *
     * @param chatId         会话主键
     * @param conversationId 会话 ID
     * @param type           对话类型
     * @param content        消息内容
     * @param references     RAG 引用文件列表
     */
    void saveAssistantMessage(Long chatId, String conversationId, ChatHistoryType type, String content,
                              List<RetrievalCitation> references);

    /**
     * 分页查询完整历史
     *
     * @param conversationId 会话 ID
     * @param current        页码，从 1 开始
     * @param size           每页数量
     * @return 历史消息分页
     */
    Page<ChatHistoryDO> pageByConversationId(String conversationId, Long current, Long size);

    /**
     * 分页查询完整历史
     *
     * @param chatId  会话主键
     * @param current 页码，从 1 开始
     * @param size    每页数量
     * @return 历史消息分页
     */
    Page<ChatHistoryDO> pageByChatId(Long chatId, Long current, Long size);
}
