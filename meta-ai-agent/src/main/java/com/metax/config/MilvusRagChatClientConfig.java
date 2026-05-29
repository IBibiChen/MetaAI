package com.metax.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MilvusRagChatClientConfig .
 *
 * <p>
 * Milvus RAG 检索增强 ChatClient 配置，三套 client 分别绑定对应 provider 的 ChatModel 和 MilvusVectorStore
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/29
 */
@Configuration
public class MilvusRagChatClientConfig {

    /**
     * DashScope Milvus RAG 检索增强 client
     *
     * <p>
     * RAG 检索增强场景，使用 DashScopeChatModel 生成回答，绑定 dashScopeMilvusVectorStore 检索同协议 embedding 写入的 Milvus 知识库内容
     *
     * @param model             DashScope 模型 (starter 自动装配)
     * @param chatMemory        对话记忆
     * @param vectorStore       DashScope Milvus 向量库
     * @param chatClientFactory ChatClient 工厂
     * @return ChatClient
     */
    @Bean
    public ChatClient dashScopeMilvusRagChatClient(DashScopeChatModel model,
                                                   ChatMemory chatMemory,
                                                   @Qualifier("dashScopeMilvusVectorStore") VectorStore vectorStore,
                                                   ChatClientFactory chatClientFactory) {
        return chatClientFactory.buildRagClient(model, chatMemory, vectorStore);
    }

    /**
     * Ollama Milvus RAG 检索增强 client
     *
     * <p>
     * RAG 检索增强场景，使用 OllamaChatModel 生成回答，绑定 ollamaMilvusVectorStore 检索同协议 embedding 写入的 Milvus 知识库内容
     *
     * @param model             Ollama 模型 (starter 自动装配)
     * @param chatMemory        对话记忆
     * @param vectorStore       Ollama Milvus 向量库
     * @param chatClientFactory ChatClient 工厂
     * @return ChatClient
     */
    @Bean
    public ChatClient ollamaMilvusRagChatClient(OllamaChatModel model,
                                                ChatMemory chatMemory,
                                                @Qualifier("ollamaMilvusVectorStore") VectorStore vectorStore,
                                                ChatClientFactory chatClientFactory) {
        return chatClientFactory.buildRagClient(model, chatMemory, vectorStore);
    }

    /**
     * OpenAI Milvus RAG 检索增强 client
     *
     * <p>
     * RAG 检索增强场景，使用 OpenAI 协议兼容模型生成回答，绑定 openAiMilvusVectorStore 检索同协议 embedding 写入的 Milvus 知识库内容
     *
     * @param model             OpenAI 兼容模型 (vLLM / TEI 等，starter 自动装配)
     * @param chatMemory        对话记忆
     * @param vectorStore       OpenAI Milvus 向量库
     * @param chatClientFactory ChatClient 工厂
     * @return ChatClient
     */
    @Bean
    public ChatClient openAiMilvusRagChatClient(OpenAiChatModel model,
                                                ChatMemory chatMemory,
                                                @Qualifier("openAiMilvusVectorStore") VectorStore vectorStore,
                                                ChatClientFactory chatClientFactory) {
        return chatClientFactory.buildRagClient(model, chatMemory, vectorStore);
    }
}
