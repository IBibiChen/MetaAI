package com.metax.config;

import com.alibaba.cloud.ai.memory.redis.RedissonRedisChatMemoryRepository;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * ChatMemoryConfig .
 *
 * <p>
 * 当前项目明确保留 Redis 与 JDBC 两套 ChatMemory
 * 默认 ChatClient 固定使用 Redis 记忆，JDBC 记忆作为候选能力保留
 *
 * <p>
 * ChatMemory 负责窗口记忆，Repository 负责底层存储
 * Redis / JDBC 不做运行时自动切换，需要 JDBC 记忆时由业务显式注入 jdbcChatMemory 创建专用 ChatClient
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/28
 */
@Configuration
public class ChatMemoryConfig {

    /**
     * Redis 对话记忆
     *
     * <p>
     * Redis 记忆场景，绑定 RedissonRedisChatMemoryRepository 作为消息存储后端
     * 当前项目必须保留 Redis 方案，因此它作为默认 ChatMemory 使用，并通过 redisChatMemory 名称供 ChatClient 显式选择
     *
     * <p>
     * Repository 负责持久化，Memory 负责窗口管理
     * Redis Repository 负责把消息写入 Redis，MessageWindowChatMemory 负责截取最近 N 条消息进入 prompt
     * MessageWindowChatMemory 超过 maxMessages 后会用裁剪后的窗口消息覆盖 Repository 中的会话记录
     * 因此 ChatMemory 不是完整历史归档，用户查看历史必须查询 ChatHistory
     * 默认窗口大小建议 10 到 20 轮，复杂助手可放宽到 20 到 50 条消息
     * 当前配置保留最近 30 条消息，避免长会话无限膨胀导致 token 成本失控
     * maxMessages 来自 metax.ai.chat.memory.max-messages，便于在不同环境调整记忆窗口大小
     * conversationId 推荐使用 tenantId:userId:sessionId，避免多租户和多会话串记忆
     *
     * @param redisChatMemoryRepository Redis 消息存储后端
     * @param maxMessages               记忆窗口消息数量
     * @return Redis ChatMemory
     */
    @Bean
    @Primary
    public ChatMemory redisChatMemory(RedissonRedisChatMemoryRepository redisChatMemoryRepository,
                                      @Value("${metax.ai.chat.memory.max-messages:30}") int maxMessages) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(redisChatMemoryRepository)
                .maxMessages(maxMessages)
                .build();
    }

    /**
     * JDBC 对话记忆
     *
     * <p>
     * JDBC 记忆场景，绑定 Spring AI 官方自动装配的 JdbcChatMemoryRepository 作为消息存储后端
     * MySQL 和 PostgreSQL 不在代码中手写 Repository，使用 spring.datasource 和 spring.ai.chat.memory.repository.jdbc 配置切换
     *
     * <p>
     * JDBC 方案适合需要审计、备份、SQL 查询、跨实例稳定持久化的企业场景
     * 同一个运行环境只启用一种 JDBC 数据库，MySQL 和 PostgreSQL 通过配置文件或 profile 切换
     * 当前项目同时保留 Redis 与 JDBC 两套 ChatMemory，再由具体 ChatClient 手动选择绑定哪套记忆
     * 默认 ChatClient 不使用该 Bean，需要 JDBC 记忆时由业务显式注入 jdbcChatMemory 创建专用 ChatClient
     *
     * @param jdbcChatMemoryRepository 官方 JDBC 消息存储后端
     * @param maxMessages              记忆窗口消息数量
     * @return JDBC ChatMemory
     */
    @Bean
    public ChatMemory jdbcChatMemory(JdbcChatMemoryRepository jdbcChatMemoryRepository,
                                     @Value("${metax.ai.chat.memory.max-messages:30}") int maxMessages) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(jdbcChatMemoryRepository)
                .maxMessages(maxMessages)
                .build();
    }


    /**
     * Redis 对话记忆 Repository
     *
     * <p>
     * 默认记忆基础设施，使用 spring.data.redis 配置连接 Redis，并通过 Spring AI Alibaba Redis Repository 持久化消息
     * database 和 keyPrefix 当前由项目统一约定，后续需要多环境差异时再抽成配置
     *
     * @param redisHost     Redis host
     * @param redisPort     Redis port
     * @param redisPassword Redis password
     * @param redisTimeout  Redis timeout
     * @return RedissonRedisChatMemoryRepository
     */
    @Bean
    public RedissonRedisChatMemoryRepository redisChatMemoryRepository(
            @Value("${spring.data.redis.host}") String redisHost,
            @Value("${spring.data.redis.port}") int redisPort,
            @Value("${spring.data.redis.password}") String redisPassword,
            @Value("${spring.data.redis.timeout}") int redisTimeout) {

        return RedissonRedisChatMemoryRepository.builder()
                .database(3)
                .keyPrefix("chat:memory:meta")
                .host(redisHost)
                .port(redisPort)
                .password(redisPassword)
                .timeout(redisTimeout)
                .build();
    }
}
