package com.metax.history;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.Instant;

/**
 * ChatHistoryServiceImpl .
 *
 * <p>
 * 基于 MyBatis-Plus 的完整聊天历史服务
 * JPA 只负责开发期自动建表，不参与业务 CRUD
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
@Service
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistoryDO> implements ChatHistoryService {

    private static final long DEFAULT_CURRENT = 1L;

    private static final long DEFAULT_SIZE = 20L;

    /**
     * 保存用户消息
     *
     * @param conversationId 会话 ID
     * @param type           对话类型
     * @param content        消息内容
     */
    @Override
    public void saveUserMessage(String conversationId, ChatHistoryType type, String content) {
        save(conversationId, type, ChatHistoryRole.USER, content);
    }

    /**
     * 保存模型回答
     *
     * @param conversationId 会话 ID
     * @param type           对话类型
     * @param content        消息内容
     */
    @Override
    public void saveAssistantMessage(String conversationId, ChatHistoryType type, String content) {
        save(conversationId, type, ChatHistoryRole.ASSISTANT, content);
    }

    /**
     * 分页查询完整历史
     *
     * @param conversationId 会话 ID
     * @param current        页码，从 1 开始
     * @param size           每页数量
     * @return MyBatis-Plus 分页结果
     */
    @Override
    public Page<ChatHistoryDO> pageByConversationId(String conversationId, Long current, Long size) {
        Assert.hasText(conversationId, "conversationId must not be blank");
        return page(Page.of(resolveCurrent(current), resolveSize(size)), query(conversationId));
    }

    private void save(String conversationId, ChatHistoryType type, ChatHistoryRole role, String content) {
        Assert.hasText(conversationId, "conversationId must not be blank");
        Assert.notNull(type, "ChatHistoryType must not be null");
        Assert.notNull(role, "ChatHistoryRole must not be null");
        Assert.hasText(content, "content must not be blank");

        ChatHistoryDO entity = new ChatHistoryDO(null, conversationId,
                type.value(), role.value(), content, Instant.now());
        save(entity);
    }

    private LambdaQueryWrapper<ChatHistoryDO> query(String conversationId) {
        return new LambdaQueryWrapper<ChatHistoryDO>()
                .eq(ChatHistoryDO::getConversationId, conversationId)
                .orderByAsc(ChatHistoryDO::getCreatedAt)
                .orderByAsc(ChatHistoryDO::getId);
    }

    private long resolveCurrent(Long current) {
        return current == null || current < DEFAULT_CURRENT ? DEFAULT_CURRENT : current;
    }

    private long resolveSize(Long size) {
        return size == null || size <= 0 ? DEFAULT_SIZE : size;
    }
}
