package com.metax.external.adapter.sync;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

import java.time.Instant;

/**
 * ExternalDocumentSyncDO .
 *
 * <p>
 * 第三方系统文档同步状态表
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/11
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "meta_external_document_sync", indexes = {
        @Index(name = "idx_external_document_sync_status", columnList = "sync_status"),
        @Index(name = "idx_external_document_sync_document", columnList = "document_id"),
        @Index(name = "idx_external_document_sync_next_attempt", columnList = "next_attempt_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_external_document_file", columnNames = {"external_file_id"})
})
@TableName("meta_external_document_sync")
@Comment("第三方系统文档同步状态表")
public class ExternalDocumentSyncDO {

    /**
     * 同步记录 ID
     */
    @Id
    @TableId(type = IdType.ASSIGN_ID)
    @Column(name = "id")
    @Comment("同步记录 ID")
    private Long id;

    /**
     * 第三方系统文件 ID
     */
    @Column(name = "external_file_id", length = 64, nullable = false)
    @Comment("第三方系统文件 ID")
    private String externalFileId;

    /**
     * 第三方系统文件路径
     */
    @Column(name = "external_file_path", length = 512)
    @Comment("第三方系统文件路径")
    private String externalFilePath;

    /**
     * 第三方系统文件哈希
     *
     * <p>
     * 保存入队时的文件 hash 快照，用于后续补偿扫描判断第三方文件是否发生变化
     */
    @Column(name = "external_hash_code", length = 128)
    @Comment("第三方系统文件哈希")
    private String externalHashCode;

    /**
     * 租户 ID
     */
    @Column(name = "tenant_id", length = 64, nullable = false)
    @Comment("租户 ID")
    private String tenantId;

    /**
     * 知识库 ID
     */
    @Column(name = "kb_id", length = 64, nullable = false)
    @Comment("知识库 ID")
    private String kbId;

    /**
     * 本系统文档 ID
     *
     * <p>
     * 对象存储上传成功后生成，后续重试会复用该 ID 重新提交索引，避免重复上传大文件
     */
    @Column(name = "document_id", length = 64)
    @Comment("本系统文档 ID")
    private String documentId;

    /**
     * 同步状态
     *
     * <p>
     * 适配器内部持久队列状态，对应 ExternalDocumentSyncStatus
     * 该状态控制 Worker 抢占、重试、索引等待和终态回写，不等同于第三方系统 status
     */
    @Column(name = "sync_status", length = 32, nullable = false)
    @Comment("同步状态")
    private String syncStatus;

    /**
     * 尝试次数
     *
     * <p>
     * 每次任务被 Worker 成功抢占时递增，用于判断是否达到最大重试次数
     */
    @Column(name = "attempt_count", nullable = false)
    @Comment("尝试次数")
    private Integer attemptCount;

    /**
     * 最后错误信息
     *
     * <p>
     * 记录最近一次失败或中断原因，便于排查下载、上传、索引等待中的具体问题
     */
    @Column(name = "last_error", length = 1000)
    @Comment("最后错误信息")
    private String lastError;

    /**
     * 下次尝试时间
     *
     * <p>
     * RETRY_WAIT 状态下使用该字段控制退避重试时间，到期后才允许再次被 Worker 抢占
     */
    @Column(name = "next_attempt_at")
    @Comment("下次尝试时间")
    private Instant nextAttemptAt;

    /**
     * 锁定执行节点
     *
     * <p>
     * 记录当前抢占任务的 Worker 标识，用于排查任务由哪个应用实例处理
     */
    @Column(name = "locked_by", length = 128)
    @Comment("锁定执行节点")
    private String lockedBy;

    /**
     * 锁定时间
     *
     * <p>
     * 和 lockTimeout 配合使用，应用异常退出后，超过锁超时时间的运行中任务可以被重新接管
     */
    @Column(name = "locked_at")
    @Comment("锁定时间")
    private Instant lockedAt;

    /**
     * 最后开始处理时间
     *
     * <p>
     * Worker 抢占任务并开始执行的时间，用于观测单个文件处理耗时
     */
    @Column(name = "last_started_at")
    @Comment("最后开始处理时间")
    private Instant lastStartedAt;

    /**
     * 最后同步时间
     *
     * <p>
     * 文件上传到对象存储并提交索引后的时间，不代表向量化已经完成
     */
    @Column(name = "last_synced_at")
    @Comment("最后同步时间")
    private Instant lastSyncedAt;

    /**
     * 完成时间
     *
     * <p>
     * 任务进入 INDEXED 或 FAILED 终态时写入，用于区分已结束任务和仍在队列中的任务
     */
    @Column(name = "finished_at")
    @Comment("完成时间")
    private Instant finishedAt;

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
