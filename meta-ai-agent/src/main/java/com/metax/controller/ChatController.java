package com.metax.controller;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    private final ChatClient dashScopeChatClient;

    private final ChatClient dashScopeRedisRagChatClient;

    private final ChatClient dashScopeQdrantRagChatClient;

    private final ChatClient dashScopeMilvusRagChatClient;

    private final ChatClient openAiChatClient;

    private final ChatClient openAiRedisRagChatClient;

    private final ChatClient openAiQdrantRagChatClient;

    private final ChatClient openAiMilvusRagChatClient;

    private final ChatClient ollamaChatClient;

    private final ChatClient ollamaRedisRagChatClient;

    private final ChatClient ollamaQdrantRagChatClient;

    private final ChatClient ollamaMilvusRagChatClient;

    private final DashScopeChatModel dashScopeChatModel;

    private final OpenAiChatModel openAiChatModel;

    private final OllamaChatModel ollamaChatModel;

    /**
     * DashScope 默认对话
     *
     * @param conversationId 会话 ID
     * @param msg            消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/dashscope/chat")
    public String dashScopeChat(@RequestParam(name = "conversationId", required = false) String conversationId,
                                @RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return chat(dashScopeChatClient, conversationId, msg);
    }

    /**
     * DashScope Redis RAG 对话
     *
     * @param conversationId 会话 ID
     * @param msg            消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/dashscope/rag/redis")
    public String dashScopeRag(@RequestParam(name = "conversationId", required = false) String conversationId,
                               @RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return chat(dashScopeRedisRagChatClient, conversationId, msg);
    }

    /**
     * DashScope Qdrant RAG 对话
     *
     * @param conversationId 会话 ID
     * @param msg            消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/dashscope/rag/qdrant")
    public String dashScopeQdrantRag(@RequestParam(name = "conversationId", required = false) String conversationId,
                                     @RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return chat(dashScopeQdrantRagChatClient, conversationId, msg);
    }

    /**
     * DashScope Milvus RAG 对话
     *
     * @param conversationId 会话 ID
     * @param msg            消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/dashscope/rag/milvus")
    public String dashScopeMilvusRag(@RequestParam(name = "conversationId", required = false) String conversationId,
                                     @RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return chat(dashScopeMilvusRagChatClient, conversationId, msg);
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
     * OpenAI 默认对话
     *
     * @param conversationId 会话 ID
     * @param msg            消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/openai/chat")
    public String openAiChat(@RequestParam(name = "conversationId", required = false) String conversationId,
                             @RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return chat(openAiChatClient, conversationId, msg);
    }

    /**
     * OpenAI Redis RAG 对话
     *
     * @param conversationId 会话 ID
     * @param msg            消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/openai/rag/redis")
    public String openAiRag(@RequestParam(name = "conversationId", required = false) String conversationId,
                            @RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return chat(openAiRedisRagChatClient, conversationId, msg);
    }

    /**
     * OpenAI Qdrant RAG 对话
     *
     * @param conversationId 会话 ID
     * @param msg            消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/openai/rag/qdrant")
    public String openAiQdrantRag(@RequestParam(name = "conversationId", required = false) String conversationId,
                                  @RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return chat(openAiQdrantRagChatClient, conversationId, msg);
    }

    /**
     * OpenAI Milvus RAG 对话
     *
     * @param conversationId 会话 ID
     * @param msg            消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/openai/rag/milvus")
    public String openAiMilvusRag(@RequestParam(name = "conversationId", required = false) String conversationId,
                                  @RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return chat(openAiMilvusRagChatClient, conversationId, msg);
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
     * Ollama 默认对话
     *
     * @param conversationId 会话 ID
     * @param msg            消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/ollama/chat")
    public String ollamaChat(@RequestParam(name = "conversationId", required = false) String conversationId,
                             @RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return chat(ollamaChatClient, conversationId, msg);
    }

    /**
     * Ollama Redis RAG 对话
     *
     * @param conversationId 会话 ID
     * @param msg            消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/ollama/rag/redis")
    public String ollamaRag(@RequestParam(name = "conversationId", required = false) String conversationId,
                            @RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return chat(ollamaRedisRagChatClient, conversationId, msg);
    }

    /**
     * Ollama Qdrant RAG 对话
     *
     * @param conversationId 会话 ID
     * @param msg            消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/ollama/rag/qdrant")
    public String ollamaQdrantRag(@RequestParam(name = "conversationId", required = false) String conversationId,
                                  @RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return chat(ollamaQdrantRagChatClient, conversationId, msg);
    }

    /**
     * Ollama Milvus RAG 对话
     *
     * @param conversationId 会话 ID
     * @param msg            消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/ollama/rag/milvus")
    public String ollamaMilvusRag(@RequestParam(name = "conversationId", required = false) String conversationId,
                                  @RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return chat(ollamaMilvusRagChatClient, conversationId, msg);
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


}
