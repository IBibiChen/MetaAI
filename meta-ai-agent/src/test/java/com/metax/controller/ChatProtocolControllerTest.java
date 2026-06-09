package com.metax.controller;

import com.metax.chat.ChatMessageController;
import com.metax.chat.ChatMessageService;
import com.metax.chat.response.ChatMessageResponse;
import com.metax.retrieval.chat.KnowledgeChatController;
import com.metax.retrieval.chat.KnowledgeChatService;
import com.metax.rag.retrieval.model.RetrievalChatResponse;
import com.metax.rag.retrieval.stream.ChatStreamDone;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ChatProtocolControllerTest .
 *
 * <p>
 * 对话接口 GET / POST 通过 stream 参数切换 JSON 和 SSE 响应的协议测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/9
 */
@WebMvcTest({
        ChatMessageController.class,
        KnowledgeChatController.class
})
class ChatProtocolControllerTest {

    private static final String API_KEY_AUTHORIZATION = "Bearer sk-metax-123456";

    @MockitoBean
    private ChatMessageService chatMessageService;

    @MockitoBean
    private KnowledgeChatService knowledgeChatService;

    @MockitoBean
    private ChatModel chatModel;

    @MockitoBean
    private ChatClient chatClient;

    @MockitoBean
    private VectorStore vectorStore;

    @jakarta.annotation.Resource
    private MockMvc mockMvc;

    @Test
    void getChatShouldReturnJsonWhenStreamMissing() throws Exception {
        when(chatMessageService.chat(any())).thenReturn(new ChatMessageResponse("answer", "c1", List.of()));

        mockMvc.perform(get("/v1/chat")
                        .param("chatId", "c1")
                        .param("tenantId", "t1")
                        .param("userId", "u1")
                        .param("msg", "hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value("answer"))
                .andExpect(jsonPath("$.data.chatId").value("c1"));
    }

    @Test
    void getChatShouldReturnSseWhenStreamTrue() throws Exception {
        when(chatMessageService.chatStream(any())).thenReturn(Flux.just(
                ServerSentEvent.builder()
                        .event("done")
                        .data(new ChatStreamDone("answer", "c1", List.of()))
                        .build()));

        mockMvc.perform(get("/v1/chat")
                        .param("chatId", "c1")
                        .param("tenantId", "t1")
                        .param("userId", "u1")
                        .param("msg", "hello")
                        .param("stream", "true"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:done")));
    }

    @Test
    void postRagShouldReturnJsonWhenStreamFalse() throws Exception {
        when(knowledgeChatService.chat(any())).thenReturn(new RetrievalChatResponse("answer", "c1",
                List.of(), List.of()));

        mockMvc.perform(post("/v1/rag")
                        .header(HttpHeaders.AUTHORIZATION, API_KEY_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "chatId": "c1",
                                  "tenantId": "t1",
                                  "userId": "u1",
                                  "kbId": "kb1",
                                  "msg": "hello",
                                  "stream": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value("answer"))
                .andExpect(jsonPath("$.data.chatId").value("c1"));
    }

    @Test
    void postRagShouldReturnSseWhenStreamTrue() throws Exception {
        when(knowledgeChatService.chatStream(any())).thenReturn(Flux.just(
                ServerSentEvent.builder()
                        .event("done")
                        .data(new ChatStreamDone("answer", "c1", List.of()))
                        .build()));

        mockMvc.perform(post("/v1/rag")
                        .header(HttpHeaders.AUTHORIZATION, API_KEY_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "chatId": "c1",
                                  "tenantId": "t1",
                                  "userId": "u1",
                                  "kbId": "kb1",
                                  "msg": "hello",
                                  "stream": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:done")));
    }
}
