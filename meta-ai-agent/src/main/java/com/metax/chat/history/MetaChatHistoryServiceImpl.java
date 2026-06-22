package com.metax.chat.history;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.metax.chat.session.MetaChatDO;
import com.metax.rag.retrieval.advisor.MetaContextFile;
import com.metax.rag.retrieval.model.RetrievalDocumentReference;
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
public class MetaChatHistoryServiceImpl extends ServiceImpl<MetaChatHistoryMapper, MetaChatHistoryDO>
        implements MetaChatHistoryService {

    private static final long DEFAULT_CURRENT = 1L;

    private static final long DEFAULT_SIZE = 20L;

    private final ObjectMapper objectMapper;

    public MetaChatHistoryServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 保存用户消息
     *
     * @param chat    会话主表记录，提供落库 fkId 和业务 chatId
     * @param type    对话类型
     * @param content 消息内容
     */
    @Override
    public void saveUserMessage(MetaChatDO chat, MetaChatHistoryType type, String content) {
        saveUserMessage(chat, type, content, List.of());
    }

    /**
     * 保存用户消息
     *
     * <p>
     * files 只记录本条用户消息显式选择的会话文件，不记录当前会话全部 READY 文件
     *
     * @param chat    会话主表记录，提供落库 fkId 和业务 chatId
     * @param type    对话类型
     * @param content 消息内容
     * @param files   本条消息关联的会话文件
     */
    @Override
    public void saveUserMessage(MetaChatDO chat, MetaChatHistoryType type, String content,
                                List<MetaContextFile> files) {
        save(chat, type, MetaChatHistoryRole.USER, content, null, files(files));
    }

    /**
     * 保存模型回答
     *
     * @param chat    会话主表记录，提供落库 fkId 和业务 chatId
     * @param type    对话类型
     * @param content 消息内容
     * @return 已保存的历史消息
     */
    @Override
    public MetaChatHistoryDO saveAssistantMessage(MetaChatDO chat, MetaChatHistoryType type, String content) {
        return saveAssistantMessage(chat, type, content, List.of());
    }

    /**
     * 保存模型回答
     *
     * @param chat       会话主表记录，提供落库 fkId 和业务 chatId
     * @param type       对话类型
     * @param content    消息内容
     * @param references 回答引用的来源文档
     * @return 已保存的历史消息
     */
    @Override
    public MetaChatHistoryDO saveAssistantMessage(MetaChatDO chat, MetaChatHistoryType type, String content,
                                                  List<RetrievalDocumentReference> references) {
        return save(chat, type, MetaChatHistoryRole.ASSISTANT, content, reference(references), null);
    }

    /**
     * 分页查询完整历史
     *
     * @param chatId  会话 ID
     * @param current 页码，从 1 开始
     * @param size    每页数量
     * @return MyBatis-Plus 分页结果
     */
    @Override
    public Page<MetaChatHistoryDO> pageByChatId(String chatId, Long current, Long size) {
        Assert.hasText(chatId, "chatId must not be blank");
        return page(Page.of(resolveCurrent(current), resolveSize(size)), query(chatId));
    }

    /**
     * 保存完整历史流水
     *
     * <p>
     * fkId 来自 meta_chat.id，只作为数据库关联字段
     * chatId 是前后端和 ChatMemory 共享的业务会话标识
     *
     * @param chat      会话主表记录
     * @param type      对话类型
     * @param role      消息角色
     * @param content   消息内容
     * @param reference 回答引用来源 JSON
     * @param files     用户消息关联文件 JSON
     * @return 已保存的历史消息
     */
    private MetaChatHistoryDO save(MetaChatDO chat, MetaChatHistoryType type, MetaChatHistoryRole role, String content,
                                   String reference, String files) {
        Assert.notNull(chat, "MetaChatDO must not be null");
        Assert.notNull(chat.getId(), "MetaChatDO id must not be null");
        Assert.hasText(chat.getChatId(), "MetaChatDO chatId must not be blank");
        Assert.notNull(type, "MetaChatHistoryType must not be null");
        Assert.notNull(role, "MetaChatHistoryRole must not be null");
        Assert.hasText(content, "content must not be blank");

        // 历史表同时保存 fkId 和 chatId，查询链路按 chatId，数据库关联按 fkId
        MetaChatHistoryDO entity = new MetaChatHistoryDO(null, chat.getId(), chat.getChatId(),
                type.value(), role.value(), content, reference, files, Instant.now());
        save(entity);
        return entity;
    }

    /**
     * 构造按 chatId 正序读取完整历史的查询条件
     *
     * @param chatId 会话 ID
     * @return MyBatis-Plus 查询条件
     */
    private LambdaQueryWrapper<MetaChatHistoryDO> query(String chatId) {
        return new LambdaQueryWrapper<MetaChatHistoryDO>()
                .eq(MetaChatHistoryDO::getChatId, chatId)
                .orderByAsc(MetaChatHistoryDO::getCreatedAt)
                .orderByAsc(MetaChatHistoryDO::getId);
    }

    /**
     * 序列化回答引用来源
     *
     * @param references 回答引用来源
     * @return 引用来源 JSON，空引用返回 null
     */
    private String reference(List<RetrievalDocumentReference> references) {
        if (references == null || references.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(references);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("回答引用来源序列化失败", e);
        }
    }

    /**
     * 序列化用户消息关联文件
     *
     * @param files 用户本轮显式选择的会话文件
     * @return 会话文件 JSON，空文件返回 null
     */
    private String files(List<MetaContextFile> files) {
        if (files == null || files.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(files);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("消息关联会话文件序列化失败", e);
        }
    }

    /**
     * 解析当前页码
     *
     * @param current 原始页码
     * @return 有效页码
     */
    private long resolveCurrent(Long current) {
        return current == null || current < DEFAULT_CURRENT ? DEFAULT_CURRENT : current;
    }

    /**
     * 解析分页大小
     *
     * @param size 原始分页大小
     * @return 有效分页大小
     */
    private long resolveSize(Long size) {
        return size == null || size <= 0 ? DEFAULT_SIZE : size;
    }
}
