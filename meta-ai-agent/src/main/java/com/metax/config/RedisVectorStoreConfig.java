package com.metax.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPooled;

/**
 * RedisVectorStoreConfig .
 *
 * <p>
 * Redis 向量库配置，三套 RAG 向量库分别绑定各自协议的 EmbeddingModel，并使用独立 Redis index / prefix
 * 不同 embedding 模型的向量维度和语义空间可能不同，禁止混用同一个向量索引
 * Spring AI RedisVectorStore 官方 builder 需要 JedisPooled，因此这里保留 Jedis 作为向量库专用客户端
 *
 * <p>
 * 三套 RedisVectorStore 共享 metadata 字段规范，写入 Document.metadata 时必须使用相同 key 才能进行过滤查询
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/28
 */
@Configuration
public class RedisVectorStoreConfig {

    /**
     * DashScope Redis RAG 向量库
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
    public RedisVectorStore dashScopeRedisVectorStore(
            @Qualifier("dashscopeEmbeddingModel") EmbeddingModel embeddingModel,
            JedisConnectionFactory jedisConnectionFactory,
            @Value("${metax.ai.vectorstore.redis.dashscope.index-name}") String indexName,
            @Value("${metax.ai.vectorstore.redis.dashscope.prefix}") String prefix,
            @Value("${spring.ai.vectorstore.redis.initialize-schema:true}") boolean initializeSchema) {

        return buildVectorStore(embeddingModel, jedisConnectionFactory, indexName, prefix, initializeSchema);
    }

    /**
     * OpenAI Redis RAG 向量库
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
    public RedisVectorStore openAiRedisVectorStore(
            @Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel,
            JedisConnectionFactory jedisConnectionFactory,
            @Value("${metax.ai.vectorstore.redis.openai.index-name}") String indexName,
            @Value("${metax.ai.vectorstore.redis.openai.prefix}") String prefix,
            @Value("${spring.ai.vectorstore.redis.initialize-schema:true}") boolean initializeSchema) {

        return buildVectorStore(embeddingModel, jedisConnectionFactory, indexName, prefix, initializeSchema);
    }

    /**
     * Ollama Redis RAG 向量库
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
    public RedisVectorStore ollamaRedisVectorStore(
            @Qualifier("ollamaEmbeddingModel") EmbeddingModel embeddingModel,
            JedisConnectionFactory jedisConnectionFactory,
            @Value("${metax.ai.vectorstore.redis.ollama.index-name}") String indexName,
            @Value("${metax.ai.vectorstore.redis.ollama.prefix}") String prefix,
            @Value("${spring.ai.vectorstore.redis.initialize-schema:true}") boolean initializeSchema) {

        return buildVectorStore(embeddingModel, jedisConnectionFactory, indexName, prefix, initializeSchema);
    }

    /**
     * 构造 Redis 向量库
     *
     * <p>
     * 每套 provider 必须传入各自的 EmbeddingModel、indexName 和 prefix
     * indexName 隔离 RediSearch 索引，prefix 隔离 Redis JSON 文档 key
     * metadataFields 固定声明可过滤字段，未声明字段即使写入 metadata 也不能用于 Redis filter expression
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
                .metadataFields(metadataFields())
                .build();
    }

    /**
     * Redis metadata 过滤字段
     *
     * <p>
     * TAG 适合精确匹配，TEXT 适合文本检索，NUMERIC 适合范围过滤
     * createdAt 建议写入 epoch millis，方便按时间范围过滤
     *
     * @return metadata fields
     */
    private MetadataField[] metadataFields() {
        return new MetadataField[]{
                MetadataField.tag("tenantId"),
                MetadataField.tag("knowledgeBaseId"),
                MetadataField.tag("documentId"),
                MetadataField.tag("documentType"),
                MetadataField.text("source"),
                MetadataField.numeric("createdAt")
        };
    }

    /**
     * 基于 Spring Boot Redis 配置创建 JedisPooled
     *
     * <p>
     * RedisVectorStore 需要 JedisPooled 原生客户端，这里复用 JedisConnectionFactory 中的连接参数
     * 避免重复维护 Redis host / port / password / timeout 配置
     * RedissonClient 用于分布式锁、限流器、队列等高阶能力，不替代这里的 JedisPooled
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
