package com.metax.chat;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.metax.chat.history.MetaChatHistoryRole;

/**
 * MetaChatService .
 *
 * <p>
 * 聊天会话主表服务
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/4
 */
public interface MetaChatService extends IService<MetaChatDO> {

    /**
     * 查询会话列表
     *
     * @param request 查询参数
     * @return 会话分页
     */
    Page<MetaChatDO> pageChats(MetaChatPageRequest request);

    /**
     * 创建或获取会话
     *
     * @param request 会话创建参数
     * @return 会话实体
     */
    MetaChatDO getOrCreate(MetaChatUpsertRequest request);

    /**
     * 更新会话最后消息
     *
     * @param chatId  会话主键
     * @param role    消息角色
     * @param content 消息内容
     */
    void updateLastMessage(Long chatId, MetaChatHistoryRole role, String content);

    /**
     * 重命名会话
     *
     * @param chatId 会话主键
     * @param title  标题
     * @return 会话实体
     */
    MetaChatDO rename(Long chatId, String title);

    /**
     * 更新会话状态
     *
     * @param chatId  会话主键
     * @param request 状态更新请求
     * @return 会话实体
     */
    MetaChatDO updateFlags(Long chatId, MetaChatFlagsRequest request);

    /**
     * 软删除会话
     *
     * @param chatId 会话主键
     */
    void softDelete(Long chatId);
}
