package com.metax.rag.indexing;

import com.metax.rag.config.RagProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * DocumentIndexingJobRepository .
 *
 * <p>
 * RAG 文档索引任务 Redis 仓储，第一版用 Redis 保存短期任务状态，后续需要审计时再扩展 JDBC 持久化
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
@Repository
public class DocumentIndexingJobRepository {

    private final RedisTemplate<String, Object> redisTemplate;

    private final RagProperties properties;

    public DocumentIndexingJobRepository(RedisTemplate<String, Object> redisTemplate, RagProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * 保存文档索引任务状态
     *
     * @param job 文档索引任务
     */
    public void save(DocumentIndexingJob job) {
        redisTemplate.opsForValue().set(key(job.jobId()), job,
                Duration.ofSeconds(properties.getIngestion().getJobTtlSeconds()));
    }

    /**
     * 查询文档索引任务状态
     *
     * @param jobId 任务 ID
     * @return 文档索引任务
     */
    public Optional<DocumentIndexingJob> findById(String jobId) {
        Object value = redisTemplate.opsForValue().get(key(jobId));
        if (value instanceof DocumentIndexingJob job) {
            return Optional.of(job);
        }
        return Optional.empty();
    }

    private String key(String jobId) {
        return properties.getIngestion().getRedisKeyPrefix() + jobId;
    }
}
