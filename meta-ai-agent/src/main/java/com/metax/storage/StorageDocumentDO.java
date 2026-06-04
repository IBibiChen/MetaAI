package com.metax.storage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

import java.time.Instant;

/**
 * StorageDocumentDO .
 *
 * <p>
 * 对象存储文档元数据实体
 * MyBatis Plus 负责业务 CRUD，JPA 只负责开发期自动建表和结构校验
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "meta_storage_document", indexes = {
        @Index(name = "idx_storage_document_kb_created", columnList = "tenant_id, knowledge_base_id, created_at"),
        @Index(name = "idx_storage_document_sha256", columnList = "tenant_id, knowledge_base_id, file_sha256"),
        @Index(name = "idx_storage_document_index_status", columnList = "index_status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_storage_document_document", columnNames = {"document_id"})
})
@TableName("meta_storage_document")
@Comment("对象存储文档元数据表")
@Schema(description = "对象存储文档元数据")
public class StorageDocumentDO {

    /**
     * 文档元数据 ID
     */
    @Id
    @TableId(type = IdType.ASSIGN_ID)
    @Column(name = "id")
    @Comment("文档元数据 ID")
    @Schema(description = "文档元数据 ID", example = "1938200000000000001")
    private Long id;

    /**
     * 租户 ID
     */
    @Column(name = "tenant_id", length = 64, nullable = false)
    @Comment("租户 ID")
    @Schema(description = "租户 ID", example = "t1")
    private String tenantId;

    /**
     * 知识库 ID
     */
    @Column(name = "knowledge_base_id", length = 64, nullable = false)
    @Comment("知识库 ID")
    @Schema(description = "知识库 ID", example = "kb1")
    private String knowledgeBaseId;

    /**
     * 文档可见性
     */
    @Column(name = "visibility", length = 32, nullable = false)
    @Comment("文档可见性")
    @Schema(description = "文档可见性", example = "PUBLIC")
    private String visibility;

    /**
     * 部门 ID
     */
    @Column(name = "dept_id", length = 64)
    @Comment("部门 ID")
    @Schema(description = "部门 ID", example = "d1")
    private String deptId;

    /**
     * 用户 ID
     */
    @Column(name = "user_id", length = 64)
    @Comment("用户 ID")
    @Schema(description = "用户 ID", example = "u1")
    private String userId;

    /**
     * 文档 ID
     */
    @Column(name = "document_id", length = 64, nullable = false)
    @Comment("文档 ID")
    @Schema(description = "文档 ID", example = "1938200000000000001")
    private String documentId;

    /**
     * 原始文件名
     */
    @Column(name = "original_filename", length = 255, nullable = false)
    @Comment("原始文件名")
    @Schema(description = "原始文件名", example = "demo.pdf")
    private String originalFilename;

    /**
     * 对象存储 bucket
     */
    @Column(name = "bucket", length = 128, nullable = false)
    @Comment("对象存储 bucket")
    @Schema(description = "对象存储 bucket", example = "meta-ai-knowledge")
    private String bucket;

    /**
     * 对象存储 object key
     */
    @Column(name = "object_key", length = 512, nullable = false)
    @Comment("对象存储 object key")
    @Schema(description = "对象存储 object key", example = "t1/kb1/1938200000000000001/demo.pdf")
    private String objectKey;

    /**
     * 内容类型
     */
    @Column(name = "content_type", length = 128)
    @Comment("内容类型")
    @Schema(description = "内容类型", example = "application/pdf")
    private String contentType;

    /**
     * 文件大小
     */
    @Column(name = "file_size", nullable = false)
    @Comment("文件大小")
    @Schema(description = "文件大小，单位：字节", example = "10240")
    private Long fileSize;

    /**
     * 文件 SHA-256
     */
    @Column(name = "file_sha256", length = 64, nullable = false)
    @Comment("文件 SHA-256")
    @Schema(description = "文件 SHA-256", example = "f2c7bb8acc97f92e987a2d4087d021b1f3f178d79e56c1502d7d91a042cfb28f")
    private String fileSha256;

    /**
     * 文档类型
     */
    @Column(name = "document_type", length = 32)
    @Comment("文档类型")
    @Schema(description = "文档类型", example = "pdf")
    private String documentType;

    /**
     * 来源标识
     */
    @Column(name = "source", length = 512)
    @Comment("来源标识")
    @Schema(description = "来源标识", example = "docs/demo.pdf")
    private String source;

    /**
     * 存储 provider
     */
    @Column(name = "storage_provider", length = 32, nullable = false)
    @Comment("存储 provider")
    @Schema(description = "存储 provider", example = "object")
    private String storageProvider;

    /**
     * 对象存储 etag
     */
    @Column(name = "storage_etag", length = 128)
    @Comment("对象存储 etag")
    @Schema(description = "对象存储 etag", example = "b10a8db164e0754105b7a99be72e3fe5")
    private String storageEtag;

    /**
     * 对象存储版本 ID
     */
    @Column(name = "storage_version_id", length = 128)
    @Comment("对象存储版本 ID")
    @Schema(description = "对象存储版本 ID", example = "v1")
    private String storageVersionId;

    /**
     * 索引状态
     */
    @Column(name = "index_status", length = 32, nullable = false)
    @Comment("索引状态")
    @Schema(description = "索引状态", example = "INDEXED")
    private String indexStatus;

    /**
     * 索引 chunk 数
     */
    @Column(name = "chunk_count", nullable = false)
    @Comment("索引 chunk 数")
    @Schema(description = "索引 chunk 数", example = "31")
    private Integer chunkCount;

    /**
     * 最新索引执行 ID
     */
    @Column(name = "latest_indexing_run_id", length = 64)
    @Comment("最新索引执行 ID")
    @Schema(description = "最新索引执行 ID", example = "1938200000000000002")
    private String latestIndexingRunId;

    /**
     * 是否启用
     */
    @Column(name = "enabled", nullable = false)
    @Comment("是否启用")
    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

    /**
     * 是否删除
     */
    @Column(name = "deleted", nullable = false)
    @Comment("是否删除")
    @Schema(description = "是否删除", example = "false")
    private Boolean deleted;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false)
    @Comment("创建时间")
    @Schema(description = "创建时间", example = "2026-06-03T11:30:00Z")
    private Instant createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at", nullable = false)
    @Comment("更新时间")
    @Schema(description = "更新时间", example = "2026-06-03T11:30:00Z")
    private Instant updatedAt;
}
