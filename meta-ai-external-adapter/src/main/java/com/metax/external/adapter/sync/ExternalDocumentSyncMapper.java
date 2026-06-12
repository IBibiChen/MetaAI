package com.metax.external.adapter.sync;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;

/**
 * ExternalDocumentSyncMapper .
 *
 * <p>
 * 第三方系统文件同步状态 MyBatis Plus Mapper
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/11
 */
@Mapper
public interface ExternalDocumentSyncMapper extends BaseMapper<ExternalDocumentSyncDO> {

    /**
     * 抢占下一条可执行同步任务
     *
     * <p>
     * 这是 PostgreSQL 队列抢占 SQL
     * FOR UPDATE SKIP LOCKED 用于避免多实例同时抢到同一条任务
     * UPDATE RETURNING 用于在一次 SQL 中完成抢占并返回更新后的记录
     *
     * @param now         当前时间
     * @param expiredAt   锁过期时间
     * @param maxAttempts 最大重试次数
     * @param workerId    Worker 标识
     * @return 已抢占的同步记录，队列为空或被其他实例抢走时返回 null
     */
    @Select("""
            update meta_external_document_sync target
            set sync_status =
                    case
                        when target.sync_status in ('PENDING', 'RETRY_WAIT') then 'DOWNLOADING'
                        else target.sync_status
                    end,
                locked_by = #{workerId},
                locked_at = #{now},
                last_started_at = #{now},
                attempt_count = coalesce(target.attempt_count, 0) + 1,
                updated_at = #{now}
            where target.id = (
                select candidate.id
                from meta_external_document_sync candidate
                where candidate.attempt_count < #{maxAttempts}
                  and (
                    (
                      candidate.sync_status in ('PENDING', 'RETRY_WAIT')
                      and (
                        candidate.next_attempt_at is null
                        or candidate.next_attempt_at <= #{now}
                      )
                    )
                    or (
                      candidate.sync_status in ('DOWNLOADING', 'STORED', 'INDEXING')
                      and candidate.locked_at is not null
                      and candidate.locked_at < #{expiredAt}
                    )
                  )
                order by
                  case candidate.sync_status
                    when 'PENDING' then 0
                    when 'RETRY_WAIT' then 1
                    when 'INDEXING' then 2
                    else 3
                  end,
                  coalesce(candidate.next_attempt_at, candidate.created_at),
                  candidate.id
                limit 1
                for update skip locked
            )
            returning
                target.id,
                target.external_file_id,
                target.external_file_path,
                target.external_hash_code,
                target.tenant_id,
                target.kb_id,
                target.document_id,
                target.sync_status,
                target.attempt_count,
                target.last_error,
                target.next_attempt_at,
                target.locked_by,
                target.locked_at,
                target.last_started_at,
                target.last_synced_at,
                target.finished_at,
                target.created_at,
                target.updated_at
            """)
    @Results(id = "ExternalDocumentSyncResultMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "external_file_id", property = "externalFileId"),
            @Result(column = "external_file_path", property = "externalFilePath"),
            @Result(column = "external_hash_code", property = "externalHashCode"),
            @Result(column = "tenant_id", property = "tenantId"),
            @Result(column = "kb_id", property = "kbId"),
            @Result(column = "document_id", property = "documentId"),
            @Result(column = "sync_status", property = "syncStatus"),
            @Result(column = "attempt_count", property = "attemptCount"),
            @Result(column = "last_error", property = "lastError"),
            @Result(column = "next_attempt_at", property = "nextAttemptAt"),
            @Result(column = "locked_by", property = "lockedBy"),
            @Result(column = "locked_at", property = "lockedAt"),
            @Result(column = "last_started_at", property = "lastStartedAt"),
            @Result(column = "last_synced_at", property = "lastSyncedAt"),
            @Result(column = "finished_at", property = "finishedAt"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    ExternalDocumentSyncDO claimNextExecutableTask(@Param("now") Instant now,
                                                   @Param("expiredAt") Instant expiredAt,
                                                   @Param("maxAttempts") int maxAttempts,
                                                   @Param("workerId") String workerId);
}
