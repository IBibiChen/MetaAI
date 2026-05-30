package com.metax.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.redisson.api.RedissonClient;
import org.redisson.spring.cache.RedissonSpringCacheManager;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * CacheConfig .
 *
 * <p>
 * Spring Cache 配置，使用 redisson-spring-boot-starter 自动装配的 RedissonClient 作为缓存后端
 * 缓存能力优先服务热点配置、RAG 元数据和模型响应等可失效数据
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/30
 */
@EnableCaching
@Configuration
public class CacheConfig {

    /**
     * 模型响应缓存名
     */
    public static final String CHAT_MODEL_RESPONSE_CACHE = "chatModelResponse";

    /**
     * RAG 文档元数据缓存名
     */
    public static final String RAG_DOCUMENT_METADATA_CACHE = "ragDocumentMetadata";

    /**
     * provider 配置缓存名
     */
    public static final String PROVIDER_CONFIG_CACHE = "providerConfig";

    /**
     * Redisson Spring CacheManager
     *
     * <p>
     * Spring Cache 基础设施，绑定 starter 自动装配的 RedissonClient，适用于 @Cacheable / @CachePut / @CacheEvict 场景
     * TTL 控制缓存总存活时间，maxIdleTime 控制空闲过期时间
     *
     * @param redissonClient RedissonClient
     * @return CacheManager
     */
    @Bean
    public CacheManager cacheManager(RedissonClient redissonClient) {
        return new RedissonSpringCacheManager(redissonClient, cacheConfigs());
    }

    /**
     * Redisson 缓存策略
     *
     * <p>
     * 不同缓存按业务数据新鲜度分配独立 TTL，避免一个全局过期时间覆盖所有场景
     *
     * @return cache config map
     */
    private Map<String, org.redisson.spring.cache.CacheConfig> cacheConfigs() {
        Map<String, org.redisson.spring.cache.CacheConfig> configs = new LinkedHashMap<>();

        configs.put(CHAT_MODEL_RESPONSE_CACHE, cacheConfig(Duration.ofMinutes(10), Duration.ofMinutes(5)));
        configs.put(RAG_DOCUMENT_METADATA_CACHE, cacheConfig(Duration.ofMinutes(30), Duration.ofMinutes(10)));
        configs.put(PROVIDER_CONFIG_CACHE, cacheConfig(Duration.ofHours(1), Duration.ofMinutes(30)));

        return configs;
    }

    /**
     * 构造 Redisson 缓存策略
     *
     * @param ttl 总存活时间
     * @param maxIdleTime 空闲过期时间
     * @return Redisson CacheConfig
     */
    private org.redisson.spring.cache.CacheConfig cacheConfig(Duration ttl, Duration maxIdleTime) {
        return new org.redisson.spring.cache.CacheConfig(ttl.toMillis(), maxIdleTime.toMillis());
    }
}
