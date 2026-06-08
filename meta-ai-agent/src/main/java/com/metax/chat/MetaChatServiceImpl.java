package com.metax.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.metax.chat.history.MetaChatHistoryRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.Instant;

/**
 * MetaChatServiceImpl .
 *
 * <p>
 * 基于 MyBatis Plus 的聊天会话主表服务
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/4
 */
@Service
public class MetaChatServiceImpl extends ServiceImpl<MetaChatMapper, MetaChatDO> implements MetaChatService {

    private static final long DEFAULT_CURRENT = 1L;

    private static final long DEFAULT_SIZE = 20L;

    private static final int DEFAULT_TITLE_LENGTH = 60;

    private static final int LAST_MESSAGE_LENGTH = 500;

    /**
     * 查询会话列表
     *
     * @param request 查询参数
     * @return 会话分页
     */
    @Override
    public Page<MetaChatDO> pageChats(MetaChatPageRequest request) {
        Assert.notNull(request, "MetaChatPageRequest must not be null");
        Assert.hasText(request.getTenantId(), "tenantId must not be blank");
        Assert.hasText(request.getUserId(), "userId must not be blank");

        LambdaQueryWrapper<MetaChatDO> query = new LambdaQueryWrapper<MetaChatDO>()
                .eq(MetaChatDO::getTenantId, request.getTenantId())
                .eq(MetaChatDO::getUserId, request.getUserId())
                .eq(MetaChatDO::getDeleted, false)
                .eq(MetaChatDO::getArchived, request.getArchived() != null && request.getArchived());
        if (StringUtils.hasText(request.getChatMode())) {
            query.eq(MetaChatDO::getChatMode, request.getChatMode());
        }
        if (request.getFavorite() != null) {
            query.eq(MetaChatDO::getFavorite, request.getFavorite());
        }
        query.orderByDesc(MetaChatDO::getPinned)
                .orderByDesc(MetaChatDO::getLastMessageAt)
                .orderByDesc(MetaChatDO::getId);
        return page(Page.of(resolveCurrent(request.getCurrent()), resolveSize(request.getSize())), query);
    }

    /**
     * 创建或获取会话
     *
     * @param request 会话创建参数
     * @return 会话实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MetaChatDO getOrCreate(MetaChatUpsertRequest request) {
        Assert.notNull(request, "MetaChatUpsertRequest must not be null");
        Assert.hasText(request.tenantId(), "tenantId must not be blank");
        Assert.hasText(request.userId(), "userId must not be blank");
        Assert.hasText(request.chatId(), "chatId must not be blank");
        Assert.notNull(request.chatMode(), "chatMode must not be null");

        MetaChatDO existing = getOne(new LambdaQueryWrapper<MetaChatDO>()
                .eq(MetaChatDO::getChatId, request.chatId()), false);
        if (existing != null) {
            reviveIfDeleted(existing);
            fillChatBinding(existing, request);
            updateById(existing);
            return existing;
        }

        Instant now = Instant.now();
        MetaChatDO entity = new MetaChatDO();
        entity.setTenantId(request.tenantId());
        entity.setUserId(request.userId());
        entity.setChatId(request.chatId());
        entity.setTitle(defaultTitle(request.firstMessage()));
        entity.setTitleEdited(false);
        entity.setSummary(null);
        entity.setLastMessage(null);
        entity.setLastRole(null);
        entity.setChatMode(request.chatMode().value());
        entity.setModelProvider(request.modelProvider());
        entity.setModelName(request.modelName());
        entity.setKnowledgeBaseId(request.knowledgeBaseId());
        entity.setSource(StringUtils.hasText(request.source()) ? request.source() : "console");
        entity.setMessageCount(0);
        entity.setPinned(false);
        entity.setFavorite(false);
        entity.setArchived(false);
        entity.setDeleted(false);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setLastMessageAt(now);
        entity.setDeletedAt(null);
        save(entity);
        return entity;
    }

    /**
     * 更新会话最后消息
     *
     * @param id      会话主键
     * @param role    消息角色
     * @param content 消息内容
     */
    @Override
    public void updateLastMessage(Long id, MetaChatHistoryRole role, String content) {
        Assert.notNull(id, "id must not be null");
        Assert.notNull(role, "MetaChatHistoryRole must not be null");
        MetaChatDO entity = requireChat(id);
        Instant now = Instant.now();
        entity.setLastMessage(truncate(content, LAST_MESSAGE_LENGTH));
        entity.setLastRole(role.value());
        entity.setMessageCount(entity.getMessageCount() == null ? 1 : entity.getMessageCount() + 1);
        entity.setLastMessageAt(now);
        entity.setUpdatedAt(now);
        updateById(entity);
    }

    /**
     * 重命名会话
     *
     * @param id    会话主键
     * @param title  标题
     * @return 会话实体
     */
    @Override
    public MetaChatDO rename(Long id, String title) {
        Assert.hasText(title, "title must not be blank");
        MetaChatDO entity = requireChat(id);
        entity.setTitle(truncate(title.trim(), 255));
        entity.setTitleEdited(true);
        entity.setUpdatedAt(Instant.now());
        updateById(entity);
        return entity;
    }

    /**
     * 更新会话状态
     *
     * @param id      会话主键
     * @param request 状态更新请求
     * @return 会话实体
     */
    @Override
    public MetaChatDO updateFlags(Long id, MetaChatFlagsRequest request) {
        Assert.notNull(request, "MetaChatFlagsRequest must not be null");
        MetaChatDO entity = requireChat(id);
        if (request.getPinned() != null) {
            entity.setPinned(request.getPinned());
        }
        if (request.getFavorite() != null) {
            entity.setFavorite(request.getFavorite());
        }
        if (request.getArchived() != null) {
            entity.setArchived(request.getArchived());
        }
        entity.setUpdatedAt(Instant.now());
        updateById(entity);
        return entity;
    }

    /**
     * 软删除会话
     *
     * @param id 会话主键
     */
    @Override
    public void softDelete(Long id) {
        MetaChatDO entity = requireChat(id);
        Instant now = Instant.now();
        entity.setDeleted(true);
        entity.setDeletedAt(now);
        entity.setUpdatedAt(now);
        updateById(entity);
    }

    private MetaChatDO requireChat(Long id) {
        Assert.notNull(id, "id must not be null");
        MetaChatDO entity = getById(id);
        if (entity == null || Boolean.TRUE.equals(entity.getDeleted())) {
            throw new IllegalArgumentException("会话不存在");
        }
        return entity;
    }

    private void reviveIfDeleted(MetaChatDO entity) {
        if (Boolean.TRUE.equals(entity.getDeleted())) {
            entity.setDeleted(false);
            entity.setDeletedAt(null);
            entity.setArchived(false);
        }
    }

    private void fillChatBinding(MetaChatDO entity, MetaChatUpsertRequest request) {
        entity.setChatMode(request.chatMode().value());
        if (StringUtils.hasText(request.knowledgeBaseId())) {
            entity.setKnowledgeBaseId(request.knowledgeBaseId());
        }
        if (StringUtils.hasText(request.modelProvider())) {
            entity.setModelProvider(request.modelProvider());
        }
        if (StringUtils.hasText(request.modelName())) {
            entity.setModelName(request.modelName());
        }
        if (StringUtils.hasText(request.source())) {
            entity.setSource(request.source());
        }
        entity.setUpdatedAt(Instant.now());
    }

    private String defaultTitle(String firstMessage) {
        if (!StringUtils.hasText(firstMessage)) {
            return "新会话";
        }
        return truncate(firstMessage.trim().replaceAll("\\s+", " "), DEFAULT_TITLE_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private long resolveCurrent(Long current) {
        return current == null || current < DEFAULT_CURRENT ? DEFAULT_CURRENT : current;
    }

    private long resolveSize(Long size) {
        return size == null || size <= 0 ? DEFAULT_SIZE : size;
    }
}
