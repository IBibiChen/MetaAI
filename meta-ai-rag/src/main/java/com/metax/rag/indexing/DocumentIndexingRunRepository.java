package com.metax.rag.indexing;

import com.metax.rag.config.MetaRetrievalProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * DocumentIndexingRunRepository .
 *
 * <p>
 * RAG 文档索引执行 Redis 仓储，第一版用 Redis 保存短期执行状态，后续需要审计时再扩展 JDBC 持久化
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
@Repository
public class DocumentIndexingRunRepository {

    private final RedisTemplate<String, Object> redisTemplate;

    private final MetaRetrievalProperties properties;

    public DocumentIndexingRunRepository(RedisTemplate<String, Object> redisTemplate, MetaRetrievalProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * 保存文档索引执行状态
     *
     * @param run 文档索引执行
     */
    public void save(DocumentIndexingRun run) {
        redisTemplate.opsForValue().set(key(run.runId()), run,
                Duration.ofSeconds(properties.getIndexing().getRunTtlSeconds()));
    }

    /**
     * 查询文档索引执行状态
     *
     * @param runId 执行 ID
     * @return 文档索引执行
     */
    public Optional<DocumentIndexingRun> findById(String runId) {
        Object value = redisTemplate.opsForValue().get(key(runId));
        if (value instanceof DocumentIndexingRun run) {
            return Optional.of(run);
        }
        return Optional.empty();
    }

    private String key(String runId) {
        return properties.getIndexing().getRedisKeyPrefix() + runId;
    }
}
