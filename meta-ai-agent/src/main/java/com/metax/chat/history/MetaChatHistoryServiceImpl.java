package com.metax.chat.history;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.metax.rag.retrieval.RetrievalCitation;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.List;

/**
 * MetaChatHistoryServiceImpl .
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
public class MetaChatHistoryServiceImpl extends ServiceImpl<MetaChatHistoryMapper, MetaChatHistoryDO> implements MetaChatHistoryService {

    private static final long DEFAULT_CURRENT = 1L;

    private static final long DEFAULT_SIZE = 20L;

    private final ObjectMapper objectMapper;

    public MetaChatHistoryServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 保存用户消息
     *
     * @param conversationId 会话 ID
     * @param type           对话类型
     * @param content        消息内容
     */
    @Override
    public void saveUserMessage(String conversationId, MetaChatHistoryType type, String content) {
        saveUserMessage(null, conversationId, type, content);
    }

    /**
     * 保存用户消息
     *
     * @param chatId         会话主键
     * @param conversationId 会话 ID
     * @param type           对话类型
     * @param content        消息内容
     */
    @Override
    public void saveUserMessage(Long chatId, String conversationId, MetaChatHistoryType type, String content) {
        save(chatId, conversationId, type, MetaChatHistoryRole.USER, content, null);
    }

    /**
     * 保存模型回答
     *
     * @param conversationId 会话 ID
     * @param type           对话类型
     * @param content        消息内容
     */
    @Override
    public void saveAssistantMessage(String conversationId, MetaChatHistoryType type, String content) {
        saveAssistantMessage(null, conversationId, type, content);
    }

    /**
     * 保存模型回答
     *
     * @param chatId         会话主键
     * @param conversationId 会话 ID
     * @param type           对话类型
     * @param content        消息内容
     */
    @Override
    public void saveAssistantMessage(Long chatId, String conversationId, MetaChatHistoryType type, String content) {
        saveAssistantMessage(chatId, conversationId, type, content, List.of());
    }

    /**
     * 保存模型回答
     *
     * @param chatId         会话主键
     * @param conversationId 会话 ID
     * @param type           对话类型
     * @param content        消息内容
     * @param references     RAG 引用文件列表
     */
    @Override
    public void saveAssistantMessage(Long chatId, String conversationId, MetaChatHistoryType type, String content,
                                     List<RetrievalCitation> references) {
        save(chatId, conversationId, type, MetaChatHistoryRole.ASSISTANT, content, referencesJson(references));
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
    public Page<MetaChatHistoryDO> pageByConversationId(String conversationId, Long current, Long size) {
        Assert.hasText(conversationId, "conversationId must not be blank");
        return page(Page.of(resolveCurrent(current), resolveSize(size)), query(conversationId));
    }

    /**
     * 分页查询完整历史
     *
     * @param chatId  会话主键
     * @param current 页码，从 1 开始
     * @param size    每页数量
     * @return MyBatis-Plus 分页结果
     */
    @Override
    public Page<MetaChatHistoryDO> pageByChatId(Long chatId, Long current, Long size) {
        Assert.notNull(chatId, "chatId must not be null");
        return page(Page.of(resolveCurrent(current), resolveSize(size)), query(chatId));
    }

    private void save(Long chatId, String conversationId, MetaChatHistoryType type, MetaChatHistoryRole role, String content,
                      String referencesJson) {
        Assert.hasText(conversationId, "conversationId must not be blank");
        Assert.notNull(type, "MetaChatHistoryType must not be null");
        Assert.notNull(role, "MetaChatHistoryRole must not be null");
        Assert.hasText(content, "content must not be blank");

        MetaChatHistoryDO entity = new MetaChatHistoryDO(null, chatId, conversationId,
                type.value(), role.value(), content, referencesJson, Instant.now());
        save(entity);
    }

    private LambdaQueryWrapper<MetaChatHistoryDO> query(String conversationId) {
        return new LambdaQueryWrapper<MetaChatHistoryDO>()
                .eq(MetaChatHistoryDO::getConversationId, conversationId)
                .orderByAsc(MetaChatHistoryDO::getCreatedAt)
                .orderByAsc(MetaChatHistoryDO::getId);
    }

    private LambdaQueryWrapper<MetaChatHistoryDO> query(Long chatId) {
        return new LambdaQueryWrapper<MetaChatHistoryDO>()
                .eq(MetaChatHistoryDO::getChatId, chatId)
                .orderByAsc(MetaChatHistoryDO::getCreatedAt)
                .orderByAsc(MetaChatHistoryDO::getId);
    }

    private String referencesJson(List<RetrievalCitation> references) {
        if (references == null || references.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(references);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("RAG 引用来源序列化失败", e);
        }
    }

    private long resolveCurrent(Long current) {
        return current == null || current < DEFAULT_CURRENT ? DEFAULT_CURRENT : current;
    }

    private long resolveSize(Long size) {
        return size == null || size <= 0 ? DEFAULT_SIZE : size;
    }
}
