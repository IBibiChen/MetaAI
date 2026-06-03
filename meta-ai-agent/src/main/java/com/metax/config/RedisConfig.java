package com.metax.config;

import com.fasterxml.jackson.databind.ObjectMapper;
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
     * 普通 Redis 数据访问使用 RedisTemplate，Redis 向量库使用 JedisPooled
     * -
     * GenericJackson2JsonRedisSerializer 适合 Java 服务内部通用对象缓存，会携带类型信息
     * 值序列化器绑定 Spring Boot 管理的 ObjectMapper 副本，继承 JavaTimeModule 等 Jackson 模块
     * 它就是为 RedisTemplate<String, Object> 这种 value 类型不固定的场景设计的
     * 当前不使用固定类型 Jackson2JsonRedisSerializer，避免 Object value 反序列化时类型信息不足
     * 跨语言数据或固定结构数据建议单独定义 typed RedisTemplate 或使用 StringRedisTemplate
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
     * @param objectMapper           Spring Boot 管理的 ObjectMapper
     * @return RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory,
                                                       ObjectMapper objectMapper) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = GenericJackson2JsonRedisSerializer.builder()
                .objectMapper(objectMapper.copy())
                .defaultTyping(true)
                .build();

        redisTemplate.setConnectionFactory(redisConnectionFactory);
        // key / hashKey 使用 string 序列化，保证 Redis key 可读
        redisTemplate.setKeySerializer(stringSerializer);
        redisTemplate.setHashKeySerializer(stringSerializer);

        // value / hashValue 使用通用 JSON 序列化，适配 Object value 类型不固定的场景
        redisTemplate.setValueSerializer(jsonSerializer);
        redisTemplate.setHashValueSerializer(jsonSerializer);

        // defaultSerializer 兜底未显式指定 serializer 的操作路径
        redisTemplate.setDefaultSerializer(jsonSerializer);

        redisTemplate.afterPropertiesSet();

        return redisTemplate;
    }

}
