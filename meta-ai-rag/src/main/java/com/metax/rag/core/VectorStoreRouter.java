package com.metax.rag.core;

import com.metax.rag.model.EmbeddingProvider;
import com.metax.rag.model.VectorStoreBackend;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * VectorStoreRouter .
 *
 * <p>
 * 轻量 VectorStore 选择器，按 provider + vectorStore 解析项目中已经显式声明的 VectorStore Bean
 * 不在这里创建 VectorStore，避免和 Redis / Qdrant / Milvus 配置类职责重叠
 *
 * <p>
 * 设计说明：为什么要按 provider + vectorStore 路由
 * 不同 EmbeddingModel 的向量维度和语义空间可能不同，不能混写同一个 collection 或 index
 * 当前项目同时有 DashScope / Ollama / OpenAI 三套 embedding，也同时有 Redis / Qdrant / Milvus 三套向量库
 * 因此写入和查询都必须显式选择同一组 provider + vectorStore
 *
 * <p>
 * Bean 名映射示例
 * <pre>{@code
 * dashscope + redis -> dashScopeRedisVectorStore
 * dashscope + qdrant -> dashScopeQdrantVectorStore
 * ollama + milvus -> ollamaMilvusVectorStore
 * openai + redis -> openAiRedisVectorStore
 * }</pre>
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
@Component
public class VectorStoreRouter {

    private final Map<String, VectorStore> vectorStores;

    public VectorStoreRouter(Map<String, VectorStore> vectorStores) {
        this.vectorStores = vectorStores;
    }

    /**
     * 获取指定 provider 和向量库后端的 VectorStore
     *
     * <p>
     * 这里只做路由，不做降级
     * 如果 Bean 不存在，直接抛错比静默切换到其他向量库更安全，因为静默切换会造成 embedding 语义空间错配
     *
     * @param provider embedding provider
     * @param backend  向量库后端
     * @return VectorStore
     */
    public VectorStore getVectorStore(EmbeddingProvider provider, VectorStoreBackend backend) {
        String beanName = provider.beanPrefix() + backend.beanSegment() + "VectorStore";
        VectorStore vectorStore = vectorStores.get(beanName);
        if (vectorStore == null) {
            throw new IllegalArgumentException("Unsupported VectorStore bean: " + beanName);
        }
        return vectorStore;
    }
}
