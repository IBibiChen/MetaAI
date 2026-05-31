package com.metax.controller;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * ChatController .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/28
 */
@RestController
@RequiredArgsConstructor
public class ChatController {

    private static final String DEFAULT_CONVERSATION_ID = "tenantId:userId:sessionId";

    private final Map<String, ChatClient> chatClients;

    private final DashScopeChatModel dashScopeChatModel;

    private final OpenAiChatModel openAiChatModel;

    private final OllamaChatModel ollamaChatModel;

    /**
     * 默认记忆对话
     *
     * <p>
     * 通过 provider 选择模型，通过 memory 选择对话记忆后端
     * 示例：/v1/dashscope/chat/redis 或 /v1/dashscope/chat/jdbc
     *
     * @param provider       模型 provider
     * @param memory         记忆后端
     * @param conversationId 会话 ID
     * @param msg            消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/{provider}/chat/{memory}")
    public String chat(@PathVariable String provider,
                       @PathVariable String memory,
                       @RequestParam(name = "conversationId", required = false) String conversationId,
                       @RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return chat(resolveDefaultChatClient(provider, memory), conversationId, msg);
    }

    /**
     * RAG 检索增强对话
     *
     * <p>
     * 通过 provider 选择模型，通过 vectorStore 选择向量库，通过 memory 选择对话记忆后端
     * 示例：/v1/dashscope/rag/redis/redis 或 /v1/dashscope/rag/qdrant/jdbc
     *
     * @param provider       模型 provider
     * @param vectorStore    向量库后端
     * @param memory         记忆后端
     * @param conversationId 会话 ID
     * @param msg            消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/{provider}/rag/{vectorStore}/{memory}")
    public String rag(@PathVariable String provider,
                      @PathVariable String vectorStore,
                      @PathVariable String memory,
                      @RequestParam(name = "conversationId", required = false) String conversationId,
                      @RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return chat(resolveRagChatClient(provider, vectorStore, memory), conversationId, msg);
    }

    /**
     * DashScope ChatModel 直连
     *
     * @param msg 消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/dashscope/model")
    public String dashScopeModel(@RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return dashScopeChatModel.call(msg);
    }

    /**
     * OpenAI ChatModel 直连
     *
     * @param msg 消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/openai/model")
    public String openAiModel(@RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return openAiChatModel.call(msg);
    }

    /**
     * Ollama ChatModel 直连
     *
     * @param msg 消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/ollama/model")
    public String ollamaModel(@RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return ollamaChatModel.call(msg);
    }

    /**
     * 使用预配置 ChatClient 对话
     *
     * @param chatClient     ChatClient
     * @param conversationId 会话 ID
     * @param msg            消息
     * @return 模型响应内容
     */
    private String chat(ChatClient chatClient, String conversationId, String msg) {
        String resolvedConversationId = conversationId == null || conversationId.isBlank()
                ? DEFAULT_CONVERSATION_ID : conversationId;

        return chatClient.prompt()
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, resolvedConversationId))
                .user(msg)
                .call()
                .content();
    }

    /**
     * 解析默认对话 ChatClient
     *
     * @param provider 模型 provider
     * @param memory   记忆后端
     * @return ChatClient
     */
    private ChatClient resolveDefaultChatClient(String provider, String memory) {
        String beanName = providerPrefix(provider) + memoryPrefix(memory) + "ChatClient";
        return getChatClient(beanName);
    }

    /**
     * 解析 RAG 检索增强 ChatClient
     *
     * @param provider    模型 provider
     * @param vectorStore 向量库后端
     * @param memory      记忆后端
     * @return ChatClient
     */
    private ChatClient resolveRagChatClient(String provider, String vectorStore, String memory) {
        String beanName = providerPrefix(provider) + memoryPrefix(memory) + vectorStorePrefix(vectorStore) + "RagChatClient";
        return getChatClient(beanName);
    }

    /**
     * 获取 ChatClient Bean
     *
     * @param beanName Bean 名称
     * @return ChatClient
     */
    private ChatClient getChatClient(String beanName) {
        ChatClient chatClient = chatClients.get(beanName);
        if (chatClient == null) {
            throw new IllegalArgumentException("Unsupported ChatClient bean: " + beanName);
        }
        return chatClient;
    }

    /**
     * provider 参数转 Bean 名前缀
     *
     * @param provider 模型 provider
     * @return Bean 名前缀
     */
    private String providerPrefix(String provider) {
        return switch (provider.toLowerCase()) {
            case "dashscope" -> "dashScope";
            case "openai" -> "openAi";
            case "ollama" -> "ollama";
            default -> throw new IllegalArgumentException("Unsupported provider: " + provider);
        };
    }

    /**
     * memory 参数转 Bean 名片段
     *
     * @param memory 记忆后端
     * @return Bean 名片段
     */
    private String memoryPrefix(String memory) {
        return switch (memory.toLowerCase()) {
            case "redis" -> "Redis";
            case "jdbc" -> "Jdbc";
            default -> throw new IllegalArgumentException("Unsupported memory: " + memory);
        };
    }

    /**
     * vectorStore 参数转 Bean 名片段
     *
     * @param vectorStore 向量库后端
     * @return Bean 名片段
     */
    private String vectorStorePrefix(String vectorStore) {
        return switch (vectorStore.toLowerCase()) {
            case "redis" -> "MemoryRedis";
            case "qdrant" -> "MemoryQdrant";
            case "milvus" -> "MemoryMilvus";
            default -> throw new IllegalArgumentException("Unsupported vectorStore: " + vectorStore);
        };
    }

}
