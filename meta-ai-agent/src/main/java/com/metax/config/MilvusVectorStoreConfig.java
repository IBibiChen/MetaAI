package com.metax.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MilvusVectorStoreConfig .
 *
 * <p>
 * Milvus 向量库配置，三套 RAG 向量库分别绑定各自协议的 EmbeddingModel，并使用独立 collection 隔离向量数据
 * 不同 embedding 模型的向量维度和语义空间可能不同，禁止混用同一个 collection
 *
 * <p>
 * Milvus collection 的 embeddingDimension 必须与对应 EmbeddingModel 输出维度一致，否则写入或检索会失败
 * Milvus 不需要 Redis MetadataField 声明，但调用侧 filterExpression 必须使用统一 metadata key
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/29
 */
@Configuration
public class MilvusVectorStoreConfig {

    /**
     * DashScope Milvus RAG 向量库
     *
     * <p>
     * RAG 场景基础设施，绑定 dashscopeEmbeddingModel，并使用 DashScope 独立 Milvus collection 隔离向量数据
     *
     * @param milvusClient       Milvus 客户端
     * @param embeddingModel     DashScope 向量模型
     * @param databaseName       Milvus database 名称
     * @param collectionName     Milvus collection 名称
     * @param embeddingDimension 向量维度
     * @param initializeSchema   是否初始化 collection schema
     * @return MilvusVectorStore
     */
    @Bean
    public MilvusVectorStore dashScopeMilvusVectorStore(
            MilvusServiceClient milvusClient,
            @Qualifier("dashscopeEmbeddingModel") EmbeddingModel embeddingModel,
            @Value("${metax.ai.vectorstore.milvus.dashscope.database-name}") String databaseName,
            @Value("${metax.ai.vectorstore.milvus.dashscope.collection-name}") String collectionName,
            @Value("${metax.ai.vectorstore.milvus.dashscope.embedding-dimension}") int embeddingDimension,
            @Value("${spring.ai.vectorstore.milvus.initialize-schema:false}") boolean initializeSchema) {

        return buildVectorStore(milvusClient, embeddingModel, databaseName, collectionName,
                embeddingDimension, initializeSchema);
    }

    /**
     * Ollama Milvus RAG 向量库
     *
     * <p>
     * RAG 场景基础设施，绑定 ollamaEmbeddingModel，并使用 Ollama 独立 Milvus collection 隔离向量数据
     *
     * @param milvusClient       Milvus 客户端
     * @param embeddingModel     Ollama 向量模型
     * @param databaseName       Milvus database 名称
     * @param collectionName     Milvus collection 名称
     * @param embeddingDimension 向量维度
     * @param initializeSchema   是否初始化 collection schema
     * @return MilvusVectorStore
     */
    @Bean
    public MilvusVectorStore ollamaMilvusVectorStore(
            MilvusServiceClient milvusClient,
            @Qualifier("ollamaEmbeddingModel") EmbeddingModel embeddingModel,
            @Value("${metax.ai.vectorstore.milvus.ollama.database-name}") String databaseName,
            @Value("${metax.ai.vectorstore.milvus.ollama.collection-name}") String collectionName,
            @Value("${metax.ai.vectorstore.milvus.ollama.embedding-dimension}") int embeddingDimension,
            @Value("${spring.ai.vectorstore.milvus.initialize-schema:false}") boolean initializeSchema) {

        return buildVectorStore(milvusClient, embeddingModel, databaseName, collectionName,
                embeddingDimension, initializeSchema);
    }

    /**
     * OpenAI Milvus RAG 向量库
     *
     * <p>
     * RAG 场景基础设施，绑定 openAiEmbeddingModel，并使用 OpenAI 独立 Milvus collection 隔离向量数据
     *
     * @param milvusClient       Milvus 客户端
     * @param embeddingModel     OpenAI 兼容向量模型 (vLLM / TEI 等)
     * @param databaseName       Milvus database 名称
     * @param collectionName     Milvus collection 名称
     * @param embeddingDimension 向量维度
     * @param initializeSchema   是否初始化 collection schema
     * @return MilvusVectorStore
     */
    @Bean
    public MilvusVectorStore openAiMilvusVectorStore(
            MilvusServiceClient milvusClient,
            @Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel,
            @Value("${metax.ai.vectorstore.milvus.openai.database-name}") String databaseName,
            @Value("${metax.ai.vectorstore.milvus.openai.collection-name}") String collectionName,
            @Value("${metax.ai.vectorstore.milvus.openai.embedding-dimension}") int embeddingDimension,
            @Value("${spring.ai.vectorstore.milvus.initialize-schema:false}") boolean initializeSchema) {

        return buildVectorStore(milvusClient, embeddingModel, databaseName, collectionName,
                embeddingDimension, initializeSchema);
    }

    /**
     * 构造 Milvus 向量库
     *
     * <p>
     * 每套 provider 必须传入各自的 EmbeddingModel、databaseName、collectionName 和 embeddingDimension
     * collectionName 隔离 Milvus collection，embeddingDimension 必须与实际模型输出维度一致
     * metadata key 由写入 Document.metadata 时决定，并在检索 filterExpression 中复用
     *
     * @param milvusClient       Milvus 客户端
     * @param embeddingModel     向量模型
     * @param databaseName       Milvus database 名称
     * @param collectionName     Milvus collection 名称
     * @param embeddingDimension 向量维度
     * @param initializeSchema   是否初始化 collection schema
     * @return MilvusVectorStore
     */
    private MilvusVectorStore buildVectorStore(MilvusServiceClient milvusClient,
                                               EmbeddingModel embeddingModel,
                                               String databaseName,
                                               String collectionName,
                                               int embeddingDimension,
                                               boolean initializeSchema) {

        return MilvusVectorStore.builder(milvusClient, embeddingModel)
                .databaseName(databaseName)
                .collectionName(collectionName)
                .embeddingDimension(embeddingDimension)
                .indexType(IndexType.IVF_FLAT)
                .metricType(MetricType.COSINE)
                .initializeSchema(initializeSchema)
                .build();
    }
}
