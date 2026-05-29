package com.metax.config;

import io.qdrant.client.QdrantClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * QdrantVectorStoreConfig .
 *
 * <p>
 * Qdrant 向量库配置，三套 RAG 向量库分别绑定各自协议的 EmbeddingModel，并使用独立 collection 隔离向量数据
 * 不同 embedding 模型的向量维度和语义空间可能不同，禁止混用同一个 collection
 *
 * <p>
 * 三套 QdrantVectorStore 共享 metadata key 规范，写入 Document.metadata 时必须使用相同 key 才能进行过滤查询
 * Qdrant 不需要 Redis MetadataField 声明，但调用侧 filterExpression 必须使用统一 metadata key
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/29
 */
@Configuration
public class QdrantVectorStoreConfig {

    /**
     * DashScope Qdrant RAG 向量库
     *
     * <p>
     * RAG 场景基础设施，绑定 dashscopeEmbeddingModel，并使用 DashScope 独立 Qdrant collection 隔离向量数据
     *
     * @param qdrantClient     Qdrant 客户端
     * @param embeddingModel   DashScope 向量模型
     * @param collectionName   Qdrant collection 名称
     * @param initializeSchema 是否初始化 collection schema
     * @return QdrantVectorStore
     */
    @Bean
    public QdrantVectorStore dashScopeQdrantVectorStore(
            QdrantClient qdrantClient,
            @Qualifier("dashscopeEmbeddingModel") EmbeddingModel embeddingModel,
            @Value("${metax.ai.vectorstore.qdrant.dashscope.collection-name}") String collectionName,
            @Value("${spring.ai.vectorstore.qdrant.initialize-schema:false}") boolean initializeSchema) {

        return buildVectorStore(qdrantClient, embeddingModel, collectionName, initializeSchema);
    }

    /**
     * Ollama Qdrant RAG 向量库
     *
     * <p>
     * RAG 场景基础设施，绑定 ollamaEmbeddingModel，并使用 Ollama 独立 Qdrant collection 隔离向量数据
     *
     * @param qdrantClient     Qdrant 客户端
     * @param embeddingModel   Ollama 向量模型
     * @param collectionName   Qdrant collection 名称
     * @param initializeSchema 是否初始化 collection schema
     * @return QdrantVectorStore
     */
    @Bean
    public QdrantVectorStore ollamaQdrantVectorStore(
            QdrantClient qdrantClient,
            @Qualifier("ollamaEmbeddingModel") EmbeddingModel embeddingModel,
            @Value("${metax.ai.vectorstore.qdrant.ollama.collection-name}") String collectionName,
            @Value("${spring.ai.vectorstore.qdrant.initialize-schema:false}") boolean initializeSchema) {

        return buildVectorStore(qdrantClient, embeddingModel, collectionName, initializeSchema);
    }

    /**
     * OpenAI Qdrant RAG 向量库
     *
     * <p>
     * RAG 场景基础设施，绑定 openAiEmbeddingModel，并使用 OpenAI 独立 Qdrant collection 隔离向量数据
     *
     * @param qdrantClient     Qdrant 客户端
     * @param embeddingModel   OpenAI 兼容向量模型 (vLLM / TEI 等)
     * @param collectionName   Qdrant collection 名称
     * @param initializeSchema 是否初始化 collection schema
     * @return QdrantVectorStore
     */
    @Bean
    public QdrantVectorStore openAiQdrantVectorStore(
            QdrantClient qdrantClient,
            @Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel,
            @Value("${metax.ai.vectorstore.qdrant.openai.collection-name}") String collectionName,
            @Value("${spring.ai.vectorstore.qdrant.initialize-schema:false}") boolean initializeSchema) {

        return buildVectorStore(qdrantClient, embeddingModel, collectionName, initializeSchema);
    }

    /**
     * 构造 Qdrant 向量库
     *
     * <p>
     * 每套 provider 必须传入各自的 EmbeddingModel 和 collectionName
     * collectionName 隔离 Qdrant collection，避免不同维度或不同语义空间的 embedding 混写
     * metadata key 由写入 Document.metadata 时决定，并在检索 filterExpression 中复用
     *
     * @param qdrantClient     Qdrant 客户端
     * @param embeddingModel   向量模型
     * @param collectionName   Qdrant collection 名称
     * @param initializeSchema 是否初始化 collection schema
     * @return QdrantVectorStore
     */
    private QdrantVectorStore buildVectorStore(QdrantClient qdrantClient,
                                               EmbeddingModel embeddingModel,
                                               String collectionName,
                                               boolean initializeSchema) {

        return QdrantVectorStore.builder(qdrantClient, embeddingModel)
                .collectionName(collectionName)
                .initializeSchema(initializeSchema)
                .build();
    }
}
