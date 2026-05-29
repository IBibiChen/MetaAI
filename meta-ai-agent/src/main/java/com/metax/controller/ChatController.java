package com.metax.controller;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
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

    @Resource
    private DashScopeChatModel chatModel;

    @Resource(name = "dashScopeMemoryClient")
    private ChatClient chatClient;


    /**
     * http://localhost:8008/v1/client/chat
     *
     * @param msg 消息
     * @return
     */
    @GetMapping(value = "/v1/client/chat")
    public String client(@RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return chatClient.prompt()
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, "tenantId:userId:sessionId"))
                .user(msg)
                .call()
                .content();
    }


    /**
     * http://localhost:8008/v1/model/chat
     *
     * @param msg 消息
     * @return
     */
    @GetMapping(value = "/v1/model/chat")
    public String model(@RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        String result = chatModel.call(msg);
        System.out.println("result = " + result);
        return result;
    }


}
