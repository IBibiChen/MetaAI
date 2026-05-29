package com.metax.controller;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
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
public class ChatController {

    private static final String DEFAULT_CONVERSATION_ID = "tenantId:userId:sessionId";

    private final ChatClient dashScopeChatClient;

    private final ChatClient dashScopeRagChatClient;

    private final ChatClient openAiChatClient;

    private final ChatClient openAiRagChatClient;

    private final ChatClient ollamaChatClient;

    private final ChatClient ollamaRagChatClient;

    private final DashScopeChatModel dashScopeChatModel;

    private final OpenAiChatModel openAiChatModel;

    private final OllamaChatModel ollamaChatModel;

    public ChatController(@Qualifier("dashScopeChatClient") ChatClient dashScopeChatClient,
                          @Qualifier("dashScopeRagChatClient") ChatClient dashScopeRagChatClient,
                          @Qualifier("openAiChatClient") ChatClient openAiChatClient,
                          @Qualifier("openAiRagChatClient") ChatClient openAiRagChatClient,
                          @Qualifier("ollamaChatClient") ChatClient ollamaChatClient,
                          @Qualifier("ollamaRagChatClient") ChatClient ollamaRagChatClient,
                          DashScopeChatModel dashScopeChatModel,
                          OpenAiChatModel openAiChatModel,
                          OllamaChatModel ollamaChatModel) {
        this.dashScopeChatClient = dashScopeChatClient;
        this.dashScopeRagChatClient = dashScopeRagChatClient;
        this.openAiChatClient = openAiChatClient;
        this.openAiRagChatClient = openAiRagChatClient;
        this.ollamaChatClient = ollamaChatClient;
        this.ollamaRagChatClient = ollamaRagChatClient;
        this.dashScopeChatModel = dashScopeChatModel;
        this.openAiChatModel = openAiChatModel;
        this.ollamaChatModel = ollamaChatModel;
    }

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
     * DashScope RAG 对话
     *
     * @param conversationId 会话 ID
     * @param msg            消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/dashscope/rag")
    public String dashScopeRag(@RequestParam(name = "conversationId", required = false) String conversationId,
                               @RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return chat(dashScopeRagChatClient, conversationId, msg);
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
     * OpenAI RAG 对话
     *
     * @param conversationId 会话 ID
     * @param msg            消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/openai/rag")
    public String openAiRag(@RequestParam(name = "conversationId", required = false) String conversationId,
                            @RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return chat(openAiRagChatClient, conversationId, msg);
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
     * Ollama RAG 对话
     *
     * @param conversationId 会话 ID
     * @param msg            消息
     * @return 模型响应内容
     */
    @GetMapping(value = "/v1/ollama/rag")
    public String ollamaRag(@RequestParam(name = "conversationId", required = false) String conversationId,
                            @RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return chat(ollamaRagChatClient, conversationId, msg);
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
