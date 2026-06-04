package com.metax.controller;

import com.metax.history.ChatHistoryService;
import com.metax.history.ChatHistoryType;
import com.metax.rag.retrieval.ChatStreamDelta;
import com.metax.rag.retrieval.ChatStreamDone;
import com.metax.rag.retrieval.ChatStreamMeta;
import com.metax.rag.retrieval.RetrievalAdvisorFactory;
import com.metax.rag.retrieval.RetrievalDecision;
import com.metax.rag.retrieval.RetrievalDecisionResult;
import com.metax.rag.retrieval.RetrievalDecisionService;
import com.metax.rag.retrieval.RetrievalFilterExpressionFactory;
import com.metax.rag.retrieval.RetrievalResponseAssembler;
import com.metax.rag.retrieval.RetrievalSearchService;
import com.metax.rag.indexing.DocumentIndexingService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatControllerStreamTest .
 *
 * <p>
 * ChatController 流式接口单元测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/4
 */
class ChatControllerStreamTest {

    @Test
    void chatStreamShouldReturnMetaDeltaAndDoneEvents() {
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        ChatController controller = controller(new TestChatModel("你", "好"), chatHistoryService,
                mock(RetrievalDecisionService.class));

        Flux<ServerSentEvent<Object>> events = controller.chatStream("c1", "你好");

        List<ServerSentEvent<Object>> result = events.collectList().block();

        assertThat(result).hasSize(4);
        assertThat(result.get(0).event()).isEqualTo("meta");
        assertThat(result.get(0).data()).isEqualTo(new ChatStreamMeta("c1"));
        assertThat(result.get(1).event()).isEqualTo("delta");
        assertThat(result.get(1).data()).isEqualTo(new ChatStreamDelta("你"));
        assertThat(result.get(2).event()).isEqualTo("delta");
        assertThat(result.get(2).data()).isEqualTo(new ChatStreamDelta("好"));
        assertThat(result.get(3).event()).isEqualTo("done");
        assertThat(result.get(3).data()).isEqualTo(new ChatStreamDone("你好", "c1", List.of()));
        verify(chatHistoryService).saveUserMessage("c1", ChatHistoryType.CHAT, "你好");
        verify(chatHistoryService).saveAssistantMessage("c1", ChatHistoryType.CHAT, "你好");
    }

    @Test
    void ragStreamShouldReturnEmptyReferencesWhenDecisionSkip() {
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        RetrievalDecisionService decisionService = mock(RetrievalDecisionService.class);
        when(decisionService.decide(any())).thenReturn(RetrievalDecisionResult.skip("skip_pattern"));
        ChatController controller = controller(new TestChatModel("回", "答"), chatHistoryService, decisionService);

        Flux<ServerSentEvent<Object>> events = controller.ragStream("c1", "你是谁", "t1", "kb1",
                null, null, null, null);

        List<ServerSentEvent<Object>> result = events.collectList().block();

        assertThat(result).hasSize(4);
        assertThat(result.get(0).event()).isEqualTo("meta");
        assertThat(result.get(1).event()).isEqualTo("delta");
        assertThat(result.get(2).event()).isEqualTo("delta");
        assertThat(result.get(3).event()).isEqualTo("done");
        assertThat(result.get(3).data()).isEqualTo(new ChatStreamDone("回答", "c1", List.of()));
        verify(chatHistoryService).saveUserMessage("c1", ChatHistoryType.RAG, "你是谁");
        verify(chatHistoryService).saveAssistantMessage("c1", ChatHistoryType.RAG, "回答");
    }

    private ChatController controller(ChatModel chatModel,
                                      ChatHistoryService chatHistoryService,
                                      RetrievalDecisionService decisionService) {
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        return new ChatController(chatClient, chatClient, chatModel, mock(VectorStore.class),
                mock(DocumentIndexingService.class), mock(RetrievalAdvisorFactory.class),
                mock(RetrievalFilterExpressionFactory.class), new RetrievalResponseAssembler(),
                mock(RetrievalSearchService.class), decisionService, chatHistoryService);
    }

    private static final class TestChatModel implements ChatModel {

        private final List<String> chunks;

        private TestChatModel(String... chunks) {
            this.chunks = List.of(chunks);
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(String.join("", chunks)))));
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.fromIterable(chunks)
                    .map(chunk -> new ChatResponse(List.of(new Generation(new AssistantMessage(chunk)))));
        }
    }
}
