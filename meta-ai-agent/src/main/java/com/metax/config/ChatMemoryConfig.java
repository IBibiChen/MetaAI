package com.metax.config;

import com.alibaba.cloud.ai.memory.redis.RedissonRedisChatMemoryRepository;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatMemoryConfig .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/28
 */
@Configuration
public class ChatMemoryConfig {

    /**
     * ChatMemory 配置 - 管理对话历史
     *
     * <p>
     * 默认记忆对话和 RAG 场景共用，绑定 RedissonRedisChatMemoryRepository 作为消息存储后端
     * 全局 RedissonClient 用于业务高阶 Redis 能力，当前 ChatMemory Repository 仍按 Spring AI Alibaba builder 独立创建
     *
     * <p>
     * -
     * 1. Repository 负责持久化，Memory 负责窗口管理
     * 2. 默认窗口大小建议 10 ∼ 20 轮，避免 token 爆炸 (普通聊天：10 ∼ 20 条消息，复杂助手：20 ∼ 50 条消息)
     * 3. 生产环境必须使用持久化 Repository
     * 4. conversationId，推荐会话键设计：tenantId:userId:sessionId
     *
     * @param redisChatMemoryRepository 消息存储后端
     * @return ChatMemory 实例
     */
    @Bean
    public ChatMemory chatMemory(RedissonRedisChatMemoryRepository redisChatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(redisChatMemoryRepository)
                .maxMessages(30)  // 保留最近 30 条消息
                .build();
    }

    // /**
    //  * 开发环境：使用内存存储
    //  * 生产环境：替换为 JDBC / Redis 等持久化实现
    //  */
    // @Bean
    // @ConditionalOnMissingBean(ChatMemoryRepository.class)
    // public ChatMemoryRepository chatMemoryRepository() {
    //     return new InMemoryChatMemoryRepository();
    // }

    @Value("${spring.data.redis.host}")
    private String redisHost;
    @Value("${spring.data.redis.port}")
    private int redisPort;
    @Value("${spring.data.redis.password}")
    private String redisPassword;
    @Value("${spring.data.redis.timeout}")
    private int redisTimeout;

    /**
     * Redis 对话记忆 Repository
     *
     * <p>
     * 默认记忆基础设施，使用 spring.data.redis 配置连接 Redis，为 ChatMemory 提供持久化消息存储
     *
     * @return RedissonRedisChatMemoryRepository
     */
    @Bean
    public RedissonRedisChatMemoryRepository redisChatMemoryRepository() {
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
