package com.metax.chat.session;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.Instant;

/**
 * MetaChatDO .
 *
 * <p>
 * 聊天会话主表实体，承载会话列表、标题、状态和会话级绑定关系
 * meta_chat_history 只保存消息流水，通过 chatId 关联到本表
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "meta_chat", indexes = {
        @Index(name = "uk_meta_chat_chat_id", columnList = "chat_id", unique = true),
        @Index(name = "idx_meta_chat_user_list",
                columnList = "tenant_id,user_id,deleted,archived,pinned,last_message_at"),
        @Index(name = "idx_meta_chat_favorite",
                columnList = "tenant_id,user_id,favorite,deleted,last_message_at")
})
@TableName("meta_chat")
@Comment("聊天会话主表")
@Schema(description = "聊天会话")
public class MetaChatDO {

    /**
     * 会话主键
     */
    @Id
    @TableId(type = IdType.ASSIGN_ID)
    @Column(name = "id")
    @Comment("会话主键")
    @Schema(description = "会话主键", example = "1938200000000000001")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 租户 ID
     */
    @Column(name = "tenant_id", length = 64, nullable = false)
    @Comment("租户 ID")
    @Schema(description = "租户 ID", example = "t1")
    private String tenantId;

    /**
     * 用户 ID
     */
    @Column(name = "user_id", length = 64, nullable = false)
    @Comment("用户 ID")
    @Schema(description = "用户 ID", example = "u1")
    private String userId;

    /**
     * 会话 ID
     */
    @Column(name = "chat_id", length = 255, nullable = false, unique = true)
    @Comment("会话 ID")
    @Schema(description = "会话 ID，建议格式：tenantId-userId-sessionId", example = "t1-u1-s1")
    private String chatId;

    /**
     * 会话标题
     */
    @Column(name = "title", length = 255, nullable = false)
    @Comment("会话标题")
    @Schema(description = "会话标题", example = "请总结知识库")
    private String title;

    /**
     * 标题是否由用户手动编辑
     */
    @Column(name = "title_edited", nullable = false)
    @Comment("标题是否由用户手动编辑")
    @Schema(description = "标题是否由用户手动编辑")
    private Boolean titleEdited;

    /**
     * 会话摘要
     */
    @Column(name = "summary", columnDefinition = "text")
    @Comment("会话摘要")
    @Schema(description = "会话摘要")
    private String summary;

    /**
     * 最后一条消息预览
     */
    @Column(name = "last_message", columnDefinition = "text")
    @Comment("最后一条消息预览")
    @Schema(description = "最后一条消息预览")
    private String lastMessage;

    /**
     * 最后一条消息角色
     */
    @Column(name = "last_role", length = 32)
    @Comment("最后一条消息角色")
    @Schema(description = "最后一条消息角色", example = "ASSISTANT")
    private String lastRole;

    /**
     * 会话模式
     */
    @Column(name = "chat_mode", length = 32, nullable = false)
    @Comment("会话模式")
    @Schema(description = "会话模式", example = "rag")
    private String chatMode;

    /**
     * 模型 provider
     */
    @Column(name = "model_provider", length = 64)
    @Comment("模型 provider")
    @Schema(description = "模型 provider", example = "dashscope")
    private String modelProvider;

    /**
     * 模型名称
     */
    @Column(name = "model_name", length = 128)
    @Comment("模型名称")
    @Schema(description = "模型名称", example = "qwen-plus")
    private String modelName;

    /**
     * 知识库 ID
     */
    @Column(name = "kb_id", length = 128)
    @Comment("知识库 ID")
    @Schema(description = "知识库 ID", example = "kb1")
    private String kbId;

    /**
     * 会话来源
     */
    @Column(name = "source", length = 64)
    @Comment("会话来源")
    @Schema(description = "会话来源", example = "console")
    private String source;

    /**
     * 消息数量
     */
    @Column(name = "message_count", nullable = false)
    @Comment("消息数量")
    @Schema(description = "消息数量", example = "8")
    private Integer messageCount;

    /**
     * 是否置顶
     */
    @Column(name = "pinned", nullable = false)
    @Comment("是否置顶")
    @Schema(description = "是否置顶")
    private Boolean pinned;

    /**
     * 是否收藏
     */
    @Column(name = "favorite", nullable = false)
    @Comment("是否收藏")
    @Schema(description = "是否收藏")
    private Boolean favorite;

    /**
     * 是否归档
     */
    @Column(name = "archived", nullable = false)
    @Comment("是否归档")
    @Schema(description = "是否归档")
    private Boolean archived;

    /**
     * 是否软删除
     */
    @Column(name = "deleted", nullable = false)
    @Comment("是否软删除")
    @Schema(description = "是否软删除")
    private Boolean deleted;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false)
    @Comment("创建时间")
    @Schema(description = "创建时间", example = "2026-06-04T11:30:00Z")
    private Instant createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at", nullable = false)
    @Comment("更新时间")
    @Schema(description = "更新时间", example = "2026-06-04T11:30:00Z")
    private Instant updatedAt;

    /**
     * 最后一条消息时间
     */
    @Column(name = "last_message_at", nullable = false)
    @Comment("最后一条消息时间")
    @Schema(description = "最后一条消息时间", example = "2026-06-04T11:30:00Z")
    private Instant lastMessageAt;

    /**
     * 删除时间
     */
    @Column(name = "deleted_at")
    @Comment("删除时间")
    @Schema(description = "删除时间", example = "2026-06-04T11:30:00Z")
    private Instant deletedAt;
}
