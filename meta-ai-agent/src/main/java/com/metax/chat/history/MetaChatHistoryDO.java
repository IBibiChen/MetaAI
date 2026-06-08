package com.metax.chat.history;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.Instant;

/**
 * MetaChatHistoryDO .
 *
 * <p>
 * 完整聊天历史实体
 * MyBatis Plus 负责业务 CRUD，JPA 只负责开发期自动建表和结构校验
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/2
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "meta_chat_history")
@TableName("meta_chat_history")
@Comment("完整聊天历史表")
@Schema(description = "完整聊天历史消息")
public class MetaChatHistoryDO {

    /**
     * 历史消息 ID
     */
    @Id
    @TableId(type = IdType.ASSIGN_ID)
    @Column(name = "id")
    @Comment("历史消息 ID")
    @Schema(description = "历史消息 ID", example = "1938200000000000001")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 会话主表 ID
     */
    @Column(name = "fk_id")
    @Comment("会话主表 ID")
    @Schema(description = "会话主表 ID", example = "1938200000000000001")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fkId;

    /**
     * 会话 ID
     */
    @Column(name = "chat_id", length = 255, nullable = false)
    @Comment("会话 ID")
    @Schema(description = "会话 ID，建议格式：tenantId:userId:sessionId", example = "t1:u1:s1")
    private String chatId;

    /**
     * 对话类型
     */
    @Column(name = "chat_type", length = 32, nullable = false)
    @Comment("对话类型")
    @Schema(description = "对话类型", example = "rag")
    private String chatType;

    /**
     * 消息角色
     */
    @Column(name = "role", length = 32, nullable = false)
    @Comment("消息角色")
    @Schema(description = "消息角色", example = "USER")
    private String role;

    /**
     * 消息内容
     */
    @Column(name = "content", columnDefinition = "text", nullable = false)
    @Comment("消息内容")
    @Schema(description = "消息内容", example = "请总结这份知识库文档")
    private String content;

    /**
     * 回答引用来源 JSON
     */
    @Column(name = "references_json", columnDefinition = "text")
    @Comment("回答引用来源 JSON")
    @Schema(description = "回答引用来源 JSON")
    private String referencesJson;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false)
    @Comment("创建时间")
    @Schema(description = "创建时间", example = "2026-06-03T11:30:00Z")
    private Instant createdAt;

}
