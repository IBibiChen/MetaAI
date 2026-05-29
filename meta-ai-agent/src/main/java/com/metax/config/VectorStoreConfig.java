package com.metax.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPooled;

/**
 * VectorStoreConfig .
 *
 * <p>
 * 三套 RAG 向量库分别绑定各自协议的 EmbeddingModel，并使用独立 Redis index / prefix
 * 不同 embedding 模型的向量维度和语义空间可能不同，禁止混用同一个向量索引
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/28
 */
@Configuration
public class VectorStoreConfig {

    /**
     * DashScope RAG 向量库
     *
     * <p>
     * RAG 场景基础设施，绑定 dashscopeEmbeddingModel，并使用 DashScope 独立 Redis indexName 和 prefix 隔离向量数据
     *
     * @param embeddingModel         DashScope 向量模型
     * @param jedisConnectionFactory Jedis 连接工厂
     * @param indexName              Redis 向量索引名
     * @param prefix                 Redis key 前缀
     * @param initializeSchema       是否初始化索引结构
     * @return RedisVectorStore
     */
    @Bean
    public RedisVectorStore dashScopeVectorStore(
            @Qualifier("dashscopeEmbeddingModel") EmbeddingModel embeddingModel,
            JedisConnectionFactory jedisConnectionFactory,
            @Value("${metax.ai.vectorstore.dashscope.index-name}") String indexName,
            @Value("${metax.ai.vectorstore.dashscope.prefix}") String prefix,
            @Value("${spring.ai.vectorstore.redis.initialize-schema:true}") boolean initializeSchema) {

        return buildVectorStore(embeddingModel, jedisConnectionFactory, indexName, prefix, initializeSchema);
    }

    /**
     * OpenAI RAG 向量库
     *
     * <p>
     * RAG 场景基础设施，绑定 openAiEmbeddingModel，并使用 OpenAI 独立 Redis indexName 和 prefix 隔离向量数据
     *
     * @param embeddingModel         OpenAI 兼容向量模型 (vLLM / TEI 等)
     * @param jedisConnectionFactory Jedis 连接工厂
     * @param indexName              Redis 向量索引名
     * @param prefix                 Redis key 前缀
     * @param initializeSchema       是否初始化索引结构
     * @return RedisVectorStore
     */
    @Bean
    public RedisVectorStore openAiVectorStore(
            @Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel,
            JedisConnectionFactory jedisConnectionFactory,
            @Value("${metax.ai.vectorstore.openai.index-name}") String indexName,
            @Value("${metax.ai.vectorstore.openai.prefix}") String prefix,
            @Value("${spring.ai.vectorstore.redis.initialize-schema:true}") boolean initializeSchema) {

        return buildVectorStore(embeddingModel, jedisConnectionFactory, indexName, prefix, initializeSchema);
    }

    /**
     * Ollama RAG 向量库
     *
     * <p>
     * RAG 场景基础设施，绑定 ollamaEmbeddingModel，并使用 Ollama 独立 Redis indexName 和 prefix 隔离向量数据
     *
     * @param embeddingModel         Ollama 向量模型
     * @param jedisConnectionFactory Jedis 连接工厂
     * @param indexName              Redis 向量索引名
     * @param prefix                 Redis key 前缀
     * @param initializeSchema       是否初始化索引结构
     * @return RedisVectorStore
     */
    @Bean
    public RedisVectorStore ollamaVectorStore(
            @Qualifier("ollamaEmbeddingModel") EmbeddingModel embeddingModel,
            JedisConnectionFactory jedisConnectionFactory,
            @Value("${metax.ai.vectorstore.ollama.index-name}") String indexName,
            @Value("${metax.ai.vectorstore.ollama.prefix}") String prefix,
            @Value("${spring.ai.vectorstore.redis.initialize-schema:true}") boolean initializeSchema) {

        return buildVectorStore(embeddingModel, jedisConnectionFactory, indexName, prefix, initializeSchema);
    }

    /**
     * 构造 Redis 向量库
     *
     * <p>
     * 每套 provider 必须传入各自的 EmbeddingModel、indexName 和 prefix
     * indexName 隔离 RediSearch 索引，prefix 隔离 Redis JSON 文档 key
     *
     * @param embeddingModel         向量模型
     * @param jedisConnectionFactory Jedis 连接工厂
     * @param indexName              Redis 向量索引名
     * @param prefix                 Redis key 前缀
     * @param initializeSchema       是否初始化索引结构
     * @return RedisVectorStore
     */
    private RedisVectorStore buildVectorStore(EmbeddingModel embeddingModel,
                                              JedisConnectionFactory jedisConnectionFactory,
                                              String indexName,
                                              String prefix,
                                              boolean initializeSchema) {

        return RedisVectorStore.builder(jedisPooled(jedisConnectionFactory), embeddingModel)
                .initializeSchema(initializeSchema)
                .indexName(indexName)
                .prefix(prefix)
                .build();
    }

    /**
     * 基于 Spring Boot Redis 配置创建 JedisPooled
     *
     * <p>
     * RedisVectorStore 需要 JedisPooled 原生客户端，这里复用 JedisConnectionFactory 中的连接参数
     * 避免重复维护 Redis host / port / password / timeout 配置
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
