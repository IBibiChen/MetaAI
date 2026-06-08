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
 * @since 2026/6/8
 */
@Service
public class MetaChatServiceImpl extends ServiceImpl<MetaChatMapper, MetaChatDO> implements MetaChatService {

    /**
     * 默认页码
     *
     * <p>
     * 仅用于会话列表查询兜底，不影响底层分页插件配置
     */
    private static final long DEFAULT_CURRENT = 1L;

    /**
     * 默认分页大小
     *
     * <p>
     * 请求未指定 size 或传入非法值时使用
     */
    private static final long DEFAULT_SIZE = 20L;

    /**
     * 默认标题最大长度
     *
     * <p>
     * 仅限制自动生成标题的展示长度，不裁剪完整消息历史
     */
    private static final int DEFAULT_TITLE_LENGTH = 60;

    /**
     * 会话列表最后消息最大长度
     *
     * <p>
     * 只用于主表摘要字段，完整正文仍保存在聊天历史表
     */
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
            // 已存在的会话直接复用，避免同一个 chatId 产生多条主表记录
            reviveIfDeleted(existing);
            fillChatBinding(existing, request);
            updateById(existing);
            return existing;
        }

        // 新会话初始化主表状态，后续消息正文由 MetaChatHistory 独立保存
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
        entity.setKbId(request.kbId());
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
        // 主表只保存列表摘要和计数，完整用户 / 助手消息由历史表承载
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

    /**
     * 获取未删除会话
     *
     * @param id 会话主键
     * @return 会话实体
     */
    private MetaChatDO requireChat(Long id) {
        Assert.notNull(id, "id must not be null");
        MetaChatDO entity = getById(id);
        if (entity == null || Boolean.TRUE.equals(entity.getDeleted())) {
            throw new IllegalArgumentException("会话不存在");
        }
        return entity;
    }

    /**
     * 恢复软删除会话
     *
     * @param entity 会话实体
     */
    private void reviveIfDeleted(MetaChatDO entity) {
        if (Boolean.TRUE.equals(entity.getDeleted())) {
            // 用户继续使用同一 chatId 时恢复主表可见状态，不新建重复会话
            entity.setDeleted(false);
            entity.setDeletedAt(null);
            entity.setArchived(false);
        }
    }

    /**
     * 刷新会话业务绑定字段
     *
     * @param entity  会话实体
     * @param request 会话创建或获取请求
     */
    private void fillChatBinding(MetaChatDO entity, MetaChatUpsertRequest request) {
        // chatMode 每次按当前入口刷新，其他绑定字段只在请求显式传入时覆盖
        entity.setChatMode(request.chatMode().value());
        if (StringUtils.hasText(request.kbId())) {
            entity.setKbId(request.kbId());
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

    /**
     * 生成默认会话标题
     *
     * @param firstMessage 首条用户消息
     * @return 会话标题
     */
    private String defaultTitle(String firstMessage) {
        if (!StringUtils.hasText(firstMessage)) {
            return "新会话";
        }
        // 自动标题压缩连续空白，避免列表展示出现换行或大量空格
        return truncate(firstMessage.trim().replaceAll("\\s+", " "), DEFAULT_TITLE_LENGTH);
    }

    /**
     * 截断展示文本
     *
     * @param value     原始文本
     * @param maxLength 最大长度
     * @return 截断后的文本
     */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 解析分页页码
     *
     * @param current 原始页码
     * @return 兜底后的页码
     */
    private long resolveCurrent(Long current) {
        return current == null || current < DEFAULT_CURRENT ? DEFAULT_CURRENT : current;
    }

    /**
     * 解析分页大小
     *
     * @param size 原始分页大小
     * @return 兜底后的分页大小
     */
    private long resolveSize(Long size) {
        return size == null || size <= 0 ? DEFAULT_SIZE : size;
    }
}
