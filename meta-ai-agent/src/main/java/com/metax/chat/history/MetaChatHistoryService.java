package com.metax.chat.history;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.metax.chat.session.MetaChatDO;
import com.metax.rag.retrieval.model.RetrievalDocumentReference;

import java.util.List;

/**
 * MetaChatHistoryService .
 *
 * <p>
 * 完整聊天历史服务
 * ChatMemory 用于模型上下文窗口，超过 maxMessages 的旧消息会被裁剪
 * MetaChatHistory 用于用户查看历史、分页查询和后续审计，不参与 prompt 构造
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/2
 */
public interface MetaChatHistoryService extends IService<MetaChatHistoryDO> {

    /**
     * 保存用户消息
     *
     * @param chat    会话主表记录，提供落库 fkId 和业务 chatId
     * @param type    对话类型
     * @param content 消息内容
     */
    void saveUserMessage(MetaChatDO chat, MetaChatHistoryType type, String content);

    /**
     * 保存模型回答
     *
     * @param chat    会话主表记录，提供落库 fkId 和业务 chatId
     * @param type    对话类型
     * @param content 消息内容
     */
    void saveAssistantMessage(MetaChatDO chat, MetaChatHistoryType type, String content);

    /**
     * 保存模型回答
     *
     * @param chat       会话主表记录，提供落库 fkId 和业务 chatId
     * @param type       对话类型
     * @param content    消息内容
     * @param references 回答引用的来源文档
     */
    void saveAssistantMessage(MetaChatDO chat, MetaChatHistoryType type, String content,
                              List<RetrievalDocumentReference> references);

    /**
     * 分页查询完整历史
     *
     * @param chatId  会话 ID
     * @param current 页码，从 1 开始
     * @param size    每页数量
     * @return 历史消息分页
     */
    Page<MetaChatHistoryDO> pageByChatId(String chatId, Long current, Long size);

}
