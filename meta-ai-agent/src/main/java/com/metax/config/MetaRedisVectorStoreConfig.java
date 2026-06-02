package com.metax.config;

import com.metax.rag.model.MetadataKeys;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;
import org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPooled;

/**
 * MetaRedisVectorStoreConfig .
 *
 * <p>
 * Redis VectorStore 轻量覆盖配置，只在 spring.ai.vectorstore.type=redis 时启用
 * spring.ai.vectorstore.type 来自 Spring AI 官方 SpringAIVectorStoreTypes.TYPE 常量
 * ChatModel 和 EmbeddingModel 仍由 Spring AI 官方 starter 自动装配
 * 这里仅补齐官方 Redis 自动配置暂未暴露的 metadataFields，保证 RAG 过滤字段可以进入 RediSearch schema
 * 当前配置依赖 JedisConnectionFactory，spring.data.redis.client-type 必须设置为 jedis
 *
 * <p>
 * Qdrant 和 Milvus 不需要这层覆盖，切换 spring.ai.vectorstore.type=qdrant 或 milvus 时直接使用官方自动装配
 * 本配置声明 vectorStore Bean 后，官方 RedisVectorStoreAutoConfiguration 会因 @ConditionalOnMissingBean 自动退让
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/2
 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.vectorstore.type", havingValue = "redis", matchIfMissing = true)
public class MetaRedisVectorStoreConfig {

    /**
     * 当前默认 Redis VectorStore
     *
     * <p>
     * RAG 向量库基础设施，绑定当前唯一 EmbeddingModel，并复用 spring.ai.vectorstore.redis 官方配置
     * metadataFields 是 Redis RediSearch 过滤能力的关键绑定关系，未声明字段即使写入 Redis JSON，也不能可靠参与 filterExpression
     * 如果启动时提示缺少 VectorStore Bean，优先检查 Spring Boot 是否创建了 JedisConnectionFactory
     *
     * @param embeddingModel         当前配置选中的 EmbeddingModel
     * @param properties             Spring AI Redis VectorStore 官方属性
     * @param jedisConnectionFactory Jedis 连接工厂
     * @param batchingStrategy       Spring AI batching strategy
     * @return RedisVectorStore
     */
    @Bean
    @ConditionalOnBean(JedisConnectionFactory.class)
    public RedisVectorStore vectorStore(EmbeddingModel embeddingModel,
                                        RedisVectorStoreProperties properties,
                                        JedisConnectionFactory jedisConnectionFactory,
                                        ObjectProvider<BatchingStrategy> batchingStrategy) {
        RedisVectorStore.Builder builder = RedisVectorStore.builder(jedisPooled(jedisConnectionFactory), embeddingModel)
                .initializeSchema(properties.isInitializeSchema())
                .indexName(properties.getIndexName())
                .prefix(properties.getPrefix())
                .metadataFields(metadataFields());

        batchingStrategy.ifAvailable(builder::batchingStrategy);
        return builder.build();
    }

    /**
     * Redis metadata 过滤字段
     *
     * <p>
     * TAG 适合租户、知识库、文档 ID、文档类型等精确过滤字段
     * TEXT 适合来源路径这种需要文本匹配的字段
     * NUMERIC 适合时间戳和 chunk 序号等范围过滤字段
     *
     * @return metadata fields
     */
    private MetadataField[] metadataFields() {
        return new MetadataField[]{
                MetadataField.tag(MetadataKeys.TENANT_ID),
                MetadataField.tag(MetadataKeys.KNOWLEDGE_BASE_ID),
                MetadataField.tag(MetadataKeys.DOCUMENT_ID),
                MetadataField.tag(MetadataKeys.DOCUMENT_TYPE),
                MetadataField.tag(MetadataKeys.CHUNK_ID),
                MetadataField.tag(MetadataKeys.CONTENT_HASH),
                MetadataField.text(MetadataKeys.SOURCE),
                MetadataField.numeric(MetadataKeys.CREATED_AT),
                MetadataField.numeric(MetadataKeys.CHUNK_INDEX)
        };
    }

    /**
     * 基于 Spring Boot Redis 配置创建 JedisPooled
     *
     * <p>
     * RedisVectorStore 需要 JedisPooled 原生客户端，这里复用 JedisConnectionFactory 中的连接参数
     * 避免 Redis host、port、password、timeout 配置重复维护
     *
     * @param jedisConnectionFactory Jedis 连接工厂
     * @return JedisPooled
     */
    private JedisPooled jedisPooled(JedisConnectionFactory jedisConnectionFactory) {
        JedisClientConfig jedisClientConfig = DefaultJedisClientConfig.builder()
                .ssl(jedisConnectionFactory.isUseSsl())
                .clientName(jedisConnectionFactory.getClientName())
                .timeoutMillis(jedisConnectionFactory.getTimeout())
                .password(jedisConnectionFactory.getPassword())
                .build();

        return new JedisPooled(new HostAndPort(jedisConnectionFactory.getHostName(), jedisConnectionFactory.getPort()),
                jedisClientConfig);
    }
}
