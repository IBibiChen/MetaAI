package com.metax.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * RedisConfig .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/28
 */
@Configuration
public class RedisConfig {

    /**
     * RedisTemplate 基础设施 Bean
     *
     * <p>
     * 默认 Redis 操作场景，绑定 RedisConnectionFactory 作为连接工厂
     * key 使用 StringRedisSerializer，value 使用 GenericJackson2JsonRedisSerializer
     *
     * <p>
     * redis-cli --raw
     * -
     * RedisTemplate 配置
     * redis 序列化的工具配置类，下面这个请一定开启配置
     * 127.0.0.1:6379> keys *
     * 1) "ord:102"  序列化过
     * 2) "\xac\xed\x00\x05t\x00\aord:102" 野生，没有序列化过
     * -
     * this.redisTemplate.opsForValue()    // 提供了操作 string 类型的所有方法
     * this.redisTemplate.opsForList()     // 提供了操作 list 类型的所有方法
     * this.redisTemplate.opsForSet()      // 提供了操作 set 的所有方法
     * this.redisTemplate.opsForHash()     // 提供了操作 hash 表的所有方法
     * this.redisTemplate.opsForZSet()     // 提供了操作 zset 的所有方法
     *
     * @param redisConnectionFactory RedisConnectionFactory
     * @return RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();

        redisTemplate.setConnectionFactory(redisConnectionFactory);
        // 设置 key 序列化方式 string
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());

        // 设置 value 的序列化方式 json，使用 GenericJackson2JsonRedisSerializer 替换默认序列化
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        redisTemplate.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        redisTemplate.afterPropertiesSet();

        return redisTemplate;
    }

}
