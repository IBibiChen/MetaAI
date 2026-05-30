package com.metax.config;

import java.net.URI;
import java.time.Duration;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * RedissonConfig .
 *
 * <p>
 * 全局 RedissonClient 配置，用于分布式锁、限流器、队列、延迟队列等 Redis 高阶能力
 * 不替代 RedisVectorStore 使用的 JedisPooled，也不替代普通 RedisTemplate 数据访问
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/30
 */
@Configuration
public class RedissonConfig {

    /**
     * Default Redis port
     */
    private static final int DEFAULT_REDIS_PORT = 6379;

    /**
     * Redis properties
     */
    private final RedisProperties redisProperties;

    /**
     * RedissonConfig
     *
     * @param redisProperties Spring Boot Redis 配置
     */
    public RedissonConfig(RedisProperties redisProperties) {
        this.redisProperties = redisProperties;
    }

    /**
     * 全局 RedissonClient
     *
     * <p>
     * 高阶 Redis 基础设施，绑定 spring.data.redis 连接配置，面向分布式锁、限流器、队列、延迟队列等场景
     * RedisVectorStore 继续使用 Spring AI 官方要求的 JedisPooled，普通 Redis 读写继续使用 RedisTemplate
     *
     * @return RedissonClient
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        SingleServerConfig singleServerConfig = config.useSingleServer()
                .setAddress(address())
                .setDatabase(database());

        if (redisProperties.getTimeout() != null) {
            singleServerConfig.setTimeout(toMillis(redisProperties.getTimeout()));
        }

        if (redisProperties.getConnectTimeout() != null) {
            singleServerConfig.setConnectTimeout(toMillis(redisProperties.getConnectTimeout()));
        }

        if (StringUtils.hasText(redisProperties.getClientName())) {
            singleServerConfig.setClientName(redisProperties.getClientName());
        }

        if (StringUtils.hasText(username())) {
            singleServerConfig.setUsername(username());
        }

        if (StringUtils.hasText(password())) {
            singleServerConfig.setPassword(password());
        }

        return Redisson.create(config);
    }

    /**
     * Redisson 单机地址
     *
     * <p>
     * 优先解析 spring.data.redis.url，未配置时使用 host / port / ssl 组合地址
     *
     * @return Redisson address
     */
    private String address() {
        if (StringUtils.hasText(redisProperties.getUrl())) {
            URI uri = URI.create(redisProperties.getUrl());
            return uri.getScheme() + "://" + uri.getHost() + ":" + port(uri);
        }

        return scheme() + "://" + redisProperties.getHost() + ":" + redisProperties.getPort();
    }

    /**
     * Redis database
     *
     * @return database index
     */
    private int database() {
        if (StringUtils.hasText(redisProperties.getUrl())) {
            URI uri = URI.create(redisProperties.getUrl());
            String path = uri.getPath();

            if (StringUtils.hasText(path) && path.length() > 1) {
                return Integer.parseInt(path.substring(1));
            }
        }

        return redisProperties.getDatabase();
    }

    /**
     * Redis username
     *
     * @return username
     */
    private String username() {
        if (StringUtils.hasText(redisProperties.getUsername())) {
            return redisProperties.getUsername();
        }

        return userInfoPart(0);
    }

    /**
     * Redis password
     *
     * @return password
     */
    private String password() {
        if (StringUtils.hasText(redisProperties.getPassword())) {
            return redisProperties.getPassword();
        }

        return userInfoPart(1);
    }

    /**
     * Redis URI userInfo 分段
     *
     * @param index 分段下标
     * @return userInfo part
     */
    private String userInfoPart(int index) {
        if (!StringUtils.hasText(redisProperties.getUrl())) {
            return null;
        }

        URI uri = URI.create(redisProperties.getUrl());
        String userInfo = uri.getUserInfo();

        if (!StringUtils.hasText(userInfo)) {
            return null;
        }

        String[] parts = userInfo.split(":", 2);

        if (parts.length <= index) {
            return null;
        }

        return parts[index];
    }

    /**
     * Redis URI port
     *
     * @param uri Redis URI
     * @return port
     */
    private int port(URI uri) {
        if (uri.getPort() > 0) {
            return uri.getPort();
        }

        return DEFAULT_REDIS_PORT;
    }

    /**
     * Redis scheme
     *
     * @return redis scheme
     */
    private String scheme() {
        if (redisProperties.getSsl().isEnabled()) {
            return "rediss";
        }

        return "redis";
    }

    /**
     * Duration 转毫秒
     *
     * @param duration duration
     * @return millis
     */
    private int toMillis(Duration duration) {
        return Math.toIntExact(duration.toMillis());
    }
}
