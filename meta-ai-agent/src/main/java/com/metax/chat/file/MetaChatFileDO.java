package com.metax.chat.file;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

import java.time.Instant;

/**
 * MetaChatFileDO .
 *
 * <p>
 * 聊天文件元数据实体
 * 聊天文件只绑定当前 chatId，不进入知识库文档表，也不被普通 RAG 检索
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "meta_chat_file", indexes = {
        @Index(name = "idx_chat_file_chat_id", columnList = "tenant_id, user_id, chat_id, created_at"),
        @Index(name = "idx_chat_file_status", columnList = "parse_status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_chat_file", columnNames = {"file_id"})
})
@TableName("meta_chat_file")
@Comment("聊天文件元数据表")
@Schema(description = "聊天文件元数据")
public class MetaChatFileDO {

    /**
     * 文件元数据 ID
     */
    @Id
    @TableId(type = IdType.ASSIGN_ID)
    @Column(name = "id")
    @Comment("文件元数据 ID")
    private Long id;

    /**
     * 文件 ID
     */
    @Column(name = "file_id", length = 64, nullable = false)
    @Comment("文件 ID")
    private String fileId;

    /**
     * 租户 ID
     */
    @Column(name = "tenant_id", length = 64, nullable = false)
    @Comment("租户 ID")
    private String tenantId;

    /**
     * 用户 ID
     */
    @Column(name = "user_id", length = 64, nullable = false)
    @Comment("用户 ID")
    private String userId;

    /**
     * 会话 ID
     */
    @Column(name = "chat_id", length = 128, nullable = false)
    @Comment("会话 ID")
    private String chatId;

    /**
     * 原始文件名
     */
    @Column(name = "original_filename", length = 255, nullable = false)
    @Comment("原始文件名")
    private String originalFilename;

    /**
     * 文档类型
     */
    @Column(name = "document_type", length = 32, nullable = false)
    @Comment("文档类型")
    private String documentType;

    /**
     * 对象存储 bucket
     */
    @Column(name = "bucket", length = 128, nullable = false)
    @Comment("对象存储 bucket")
    private String bucket;

    /**
     * 对象存储 object key
     */
    @Column(name = "object_key", length = 512, nullable = false)
    @Comment("对象存储 object key")
    private String objectKey;

    /**
     * 内容类型
     */
    @Column(name = "content_type", length = 128)
    @Comment("内容类型")
    private String contentType;

    /**
     * 文件大小
     */
    @Column(name = "file_size", nullable = false)
    @Comment("文件大小")
    private Long fileSize;

    /**
     * 文件 SHA-256
     */
    @Column(name = "file_sha256", length = 64, nullable = false)
    @Comment("文件 SHA-256")
    private String fileSha256;

    /**
     * 解析状态
     */
    @Column(name = "parse_status", length = 32, nullable = false)
    @Comment("解析状态")
    private String parseStatus;

    /**
     * 临时索引 chunk 数
     */
    @Column(name = "chunk_count", nullable = false)
    @Comment("临时索引 chunk 数")
    private Integer chunkCount;

    /**
     * 是否删除
     */
    @Column(name = "deleted", nullable = false)
    @Comment("是否删除")
    private Boolean deleted;

    /**
     * 过期时间
     */
    @Column(name = "expires_at")
    @Comment("过期时间")
    private Instant expiresAt;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false)
    @Comment("创建时间")
    private Instant createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at", nullable = false)
    @Comment("更新时间")
    private Instant updatedAt;
}
