package com.metax.controller;

import com.metax.chat.ChatMessageService;
import com.metax.chat.file.MetaChatFileService;
import com.metax.chat.history.MetaChatHistoryRole;
import com.metax.chat.history.MetaChatHistoryService;
import com.metax.chat.history.MetaChatHistoryType;
import com.metax.chat.session.MetaChatDO;
import com.metax.chat.session.MetaChatService;
import com.metax.chat.support.ChatHistoryRecorder;
import com.metax.chat.support.ChatScopeResolver;
import com.metax.chat.support.ChatStreamEventAssembler;
import com.metax.chat.support.ContextFileChatSupport;
import com.metax.chat.request.ChatRequest;
import com.metax.chat.response.ChatMessageResponse;
import com.metax.retrieval.chat.KnowledgeChatService;
import com.metax.retrieval.chat.RetrievalOptionsFactory;
import com.metax.retrieval.chat.request.RetrievalChatRequest;
import com.metax.rag.model.MetadataKeys;
import com.metax.rag.retrieval.advisor.MetaContextFile;
import com.metax.rag.retrieval.advisor.MetaContextFileAdvisor;
import com.metax.rag.retrieval.advisor.RetrievalAdvisorFactory;
import com.metax.rag.retrieval.assembly.RetrievalResponseAssembler;
import com.metax.rag.retrieval.decision.RetrievalDecisionResult;
import com.metax.rag.retrieval.decision.RetrievalDecisionService;
import com.metax.rag.retrieval.filter.RetrievalFilterExpressionFactory;
import com.metax.rag.retrieval.model.RetrievalChatResponse;
import com.metax.rag.retrieval.stream.ChatStreamDelta;
import com.metax.rag.retrieval.stream.ChatStreamDone;
import com.metax.rag.retrieval.stream.ChatStreamMeta;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatApplicationStreamTest .
 *
 * <p>
 * 聊天流式应用服务单元测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
class ChatApplicationStreamTest {

    @Test
    void chatStreamShouldReturnMetaDeltaAndDoneEvents() {
        MetaChatHistoryService metaChatHistoryService = mock(MetaChatHistoryService.class);
        MetaChatService metaChatService = metaChatService();
        ChatMessageService service = chatMessageService(new TestChatModel("你", "好"),
                metaChatHistoryService, metaChatService);

        Flux<ServerSentEvent<Object>> events = service.chatStream(chatRequest("c1", "t1", "u1", "你好"));

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
        verify(metaChatHistoryService).saveUserMessage(1L, "c1", MetaChatHistoryType.CHAT, "你好");
        verify(metaChatHistoryService).saveAssistantMessage(eq(1L), eq("c1"), eq(MetaChatHistoryType.CHAT),
                eq("你好"), eq(List.of()));
        verify(metaChatService).updateLastMessage(1L, MetaChatHistoryRole.USER, "你好");
        verify(metaChatService).updateLastMessage(1L, MetaChatHistoryRole.ASSISTANT, "你好");
    }

    @Test
    void ragStreamShouldReturnEmptyReferencesWhenDecisionSkip() {
        MetaChatHistoryService metaChatHistoryService = mock(MetaChatHistoryService.class);
        MetaChatService metaChatService = metaChatService();
        RetrievalDecisionService decisionService = mock(RetrievalDecisionService.class);
        when(decisionService.decide(any())).thenReturn(RetrievalDecisionResult.skip("skip_pattern"));
        KnowledgeChatService service = knowledgeChatService(new TestChatModel("回", "答"),
                metaChatHistoryService, metaChatService, decisionService);

        Flux<ServerSentEvent<Object>> events = service.chatStream(retrievalChatRequest("c1", "你是谁", "t1",
                "kb1"));

        List<ServerSentEvent<Object>> result = events.collectList().block();

        assertThat(result).hasSize(4);
        assertThat(result.get(0).event()).isEqualTo("meta");
        assertThat(result.get(1).event()).isEqualTo("delta");
        assertThat(result.get(2).event()).isEqualTo("delta");
        assertThat(result.get(3).event()).isEqualTo("done");
        assertThat(result.get(3).data()).isEqualTo(new ChatStreamDone("回答", "c1", List.of()));
        verify(metaChatHistoryService).saveUserMessage(1L, "c1", MetaChatHistoryType.RAG, "你是谁");
        verify(metaChatHistoryService).saveAssistantMessage(eq(1L), eq("c1"), eq(MetaChatHistoryType.RAG),
                eq("回答"), eq(List.of()));
        verify(metaChatService).updateLastMessage(1L, MetaChatHistoryRole.USER, "你是谁");
        verify(metaChatService).updateLastMessage(1L, MetaChatHistoryRole.ASSISTANT, "回答");
    }

    @Test
    void chatJsonStreamShouldUseExplicitReadyFileIds() {
        MetaChatHistoryService metaChatHistoryService = mock(MetaChatHistoryService.class);
        MetaChatService metaChatService = metaChatService();
        MetaChatFileService fileService = mock(MetaChatFileService.class);
        MetaContextFile file = file("file-1", "demo.pdf", "pdf");
        when(fileService.readyFiles("t1", "u1", "c1", List.of("file-1"))).thenReturn(List.of(file));
        when(fileService.retrieve(eq("t1"), eq("u1"), eq("c1"), eq(List.of(file)), eq("总结文件")))
                .thenReturn(List.of(Document.builder()
                        .text("文件内容")
                        .metadata(Map.of(MetadataKeys.FILE_NAME, "demo.pdf", MetadataKeys.CHUNK_INDEX, 0))
                        .build()));
        ChatMessageService service = chatMessageService(new TestChatModel("文", "件"),
                metaChatHistoryService, metaChatService, fileService);

        List<ServerSentEvent<Object>> result = service.chatStream(chatRequest("c1", "t1", "u1",
                "总结文件", List.of("file-1"))).collectList().block();

        assertThat(result).hasSize(4);
        assertThat(result.get(3).event()).isEqualTo("done");
        assertThat(result.get(3).data()).isEqualTo(new ChatStreamDone("文件", "c1", List.of(), List.of(file)));
        verify(fileService).readyFiles("t1", "u1", "c1", List.of("file-1"));
        verify(fileService).retrieve("t1", "u1", "c1", List.of(file), "总结文件");
    }

    @Test
    void chatGetStreamShouldUseFileIdsFromQuery() {
        MetaChatHistoryService metaChatHistoryService = mock(MetaChatHistoryService.class);
        MetaChatService metaChatService = metaChatService();
        MetaChatFileService fileService = mock(MetaChatFileService.class);
        MetaContextFile first = file("file-1", "first.pdf", "pdf");
        MetaContextFile second = file("file-2", "second.pdf", "pdf");
        when(fileService.readyFiles("t1", "u1", "c1", List.of("file-1", "file-2")))
                .thenReturn(List.of(first, second));
        when(fileService.retrieve(eq("t1"), eq("u1"), eq("c1"), eq(List.of(first, second)), eq("总结文件")))
                .thenReturn(List.of(Document.builder()
                        .text("文件内容")
                        .metadata(Map.of(MetadataKeys.FILE_NAME, "first.pdf", MetadataKeys.CHUNK_INDEX, 0))
                        .build()));
        ChatMessageService service = chatMessageService(new TestChatModel("文", "件"),
                metaChatHistoryService, metaChatService, fileService);

        List<ServerSentEvent<Object>> result = service.chatStream(chatRequest("c1", "t1", "u1",
                "总结文件", List.of("file-1,file-2"))).collectList().block();

        assertThat(result).hasSize(4);
        assertThat(result.get(3).event()).isEqualTo("done");
        assertThat(result.get(3).data()).isEqualTo(new ChatStreamDone("文件", "c1", List.of(),
                List.of(first, second)));
        verify(fileService).readyFiles("t1", "u1", "c1", List.of("file-1", "file-2"));
    }

    @Test
    void ragJsonStreamShouldFallbackReadyFilesWhenFileIdsEmpty() {
        MetaChatHistoryService metaChatHistoryService = mock(MetaChatHistoryService.class);
        MetaChatService metaChatService = metaChatService();
        MetaChatFileService fileService = mock(MetaChatFileService.class);
        RetrievalDecisionService decisionService = mock(RetrievalDecisionService.class);
        MetaContextFile file = file("file-1", "demo.pdf", "pdf");
        when(decisionService.decide(any())).thenReturn(RetrievalDecisionResult.skip("skip_pattern"));
        when(fileService.readyFiles("t1", "u1", "c1")).thenReturn(List.of(file));
        when(fileService.retrieve(eq("t1"), eq("u1"), eq("c1"), eq(List.of(file)), eq("继续总结")))
                .thenReturn(List.of(Document.builder()
                        .text("历史文件内容")
                        .metadata(Map.of(MetadataKeys.FILE_NAME, "demo.pdf", MetadataKeys.CHUNK_INDEX, 0))
                        .build()));
        KnowledgeChatService service = knowledgeChatService(new TestChatModel("回", "答"),
                metaChatHistoryService, metaChatService, decisionService, fileService);

        List<ServerSentEvent<Object>> result = service.chatStream(retrievalChatRequest("c1",
                "继续总结", "t1", "kb1", "u1", List.of())).collectList().block();

        assertThat(result).hasSize(4);
        assertThat(result.get(3).event()).isEqualTo("done");
        assertThat(result.get(3).data()).isEqualTo(new ChatStreamDone("回答", "c1", List.of(), List.of(file)));
        verify(fileService).readyFiles("t1", "u1", "c1");
        verify(fileService).retrieve("t1", "u1", "c1", List.of(file), "继续总结");
    }

    @Test
    void ragGetStreamShouldUseExplicitReadyFileIds() {
        MetaChatHistoryService metaChatHistoryService = mock(MetaChatHistoryService.class);
        MetaChatService metaChatService = metaChatService();
        MetaChatFileService fileService = mock(MetaChatFileService.class);
        RetrievalDecisionService decisionService = mock(RetrievalDecisionService.class);
        MetaContextFile file = file("file-1", "demo.pdf", "pdf");
        when(decisionService.decide(any())).thenReturn(RetrievalDecisionResult.skip("skip_pattern"));
        when(fileService.readyFiles("t1", "u1", "c1", List.of("file-1"))).thenReturn(List.of(file));
        when(fileService.retrieve(eq("t1"), eq("u1"), eq("c1"), eq(List.of(file)), eq("继续总结")))
                .thenReturn(List.of(Document.builder()
                        .text("历史文件内容")
                        .metadata(Map.of(MetadataKeys.FILE_NAME, "demo.pdf", MetadataKeys.CHUNK_INDEX, 0))
                        .build()));
        KnowledgeChatService service = knowledgeChatService(new TestChatModel("回", "答"),
                metaChatHistoryService, metaChatService, decisionService, fileService);

        List<ServerSentEvent<Object>> result = service.chatStream(retrievalChatRequest("c1", "继续总结",
                "t1", "kb1", "u1", List.of("file-1"))).collectList().block();

        assertThat(result).hasSize(4);
        assertThat(result.get(3).event()).isEqualTo("done");
        assertThat(result.get(3).data()).isEqualTo(new ChatStreamDone("回答", "c1", List.of(), List.of(file)));
        verify(fileService).readyFiles("t1", "u1", "c1", List.of("file-1"));
    }

    @Test
    void chatShouldUseExplicitReadyFileIds() {
        MetaChatHistoryService metaChatHistoryService = mock(MetaChatHistoryService.class);
        MetaChatService metaChatService = metaChatService();
        MetaChatFileService fileService = mock(MetaChatFileService.class);
        MetaContextFile file = file("file-1", "demo.pdf", "pdf");
        when(fileService.readyFiles("t1", "u1", "c1", List.of("file-1"))).thenReturn(List.of(file));
        when(fileService.retrieve(eq("t1"), eq("u1"), eq("c1"), eq(List.of(file)), eq("总结文件")))
                .thenReturn(List.of(Document.builder()
                        .text("文件内容")
                        .metadata(Map.of(MetadataKeys.FILE_NAME, "demo.pdf", MetadataKeys.CHUNK_INDEX, 0))
                        .build()));
        ChatMessageService service = chatMessageService(new TestChatModel("文件回答"),
                metaChatHistoryService, metaChatService, fileService);

        ChatMessageResponse response = service.chat(chatRequest("c1", "t1", "u1", "总结文件",
                List.of("file-1")));

        assertThat(response.answer()).isEqualTo("文件回答");
        assertThat(response.chatId()).isEqualTo("c1");
        assertThat(response.files()).containsExactly(file);
        verify(fileService).readyFiles("t1", "u1", "c1", List.of("file-1"));
        verify(fileService).retrieve("t1", "u1", "c1", List.of(file), "总结文件");
        verify(metaChatHistoryService).saveUserMessage(1L, "c1", MetaChatHistoryType.FILE_CHAT, "总结文件");
        verify(metaChatHistoryService).saveAssistantMessage(1L, "c1", MetaChatHistoryType.FILE_CHAT,
                "文件回答");
    }

    @Test
    void ragChatShouldFallbackReadyFilesWhenFileIdsEmpty() {
        MetaChatHistoryService metaChatHistoryService = mock(MetaChatHistoryService.class);
        MetaChatService metaChatService = metaChatService();
        MetaChatFileService fileService = mock(MetaChatFileService.class);
        RetrievalDecisionService decisionService = mock(RetrievalDecisionService.class);
        MetaContextFile file = file("file-1", "demo.pdf", "pdf");
        when(decisionService.decide(any())).thenReturn(RetrievalDecisionResult.skip("skip_pattern"));
        when(fileService.readyFiles("t1", "u1", "c1")).thenReturn(List.of(file));
        when(fileService.retrieve(eq("t1"), eq("u1"), eq("c1"), eq(List.of(file)), eq("继续总结")))
                .thenReturn(List.of(Document.builder()
                        .text("历史文件内容")
                        .metadata(Map.of(MetadataKeys.FILE_NAME, "demo.pdf", MetadataKeys.CHUNK_INDEX, 0))
                        .build()));
        KnowledgeChatService service = knowledgeChatService(new TestChatModel("RAG 文件回答"),
                metaChatHistoryService, metaChatService, decisionService, fileService);

        RetrievalChatResponse response = service.chat(retrievalChatRequest("c1", "继续总结", "t1",
                "kb1", "u1", List.of()));

        assertThat(response.answer()).isEqualTo("RAG 文件回答");
        assertThat(response.chatId()).isEqualTo("c1");
        assertThat(response.references()).isEmpty();
        assertThat(response.files()).containsExactly(file);
        verify(fileService).readyFiles("t1", "u1", "c1");
        verify(fileService).retrieve("t1", "u1", "c1", List.of(file), "继续总结");
    }

    private ChatMessageService chatMessageService(ChatModel chatModel,
                                                  MetaChatHistoryService metaChatHistoryService,
                                                  MetaChatService metaChatService) {
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        MetaChatFileService fileService = mock(MetaChatFileService.class);
        return chatMessageService(chatModel, metaChatHistoryService, metaChatService, fileService);
    }

    private ChatMessageService chatMessageService(ChatModel chatModel,
                                                  MetaChatHistoryService metaChatHistoryService,
                                                  MetaChatService metaChatService,
                                                  MetaChatFileService fileService) {
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        ChatScopeResolver chatScopeResolver = new ChatScopeResolver();
        ChatHistoryRecorder chatHistoryRecorder = new ChatHistoryRecorder(metaChatService, metaChatHistoryService,
                chatScopeResolver);
        RetrievalResponseAssembler retrievalResponseAssembler = new RetrievalResponseAssembler();
        ChatStreamEventAssembler streamEventAssembler = new ChatStreamEventAssembler(chatHistoryRecorder,
                retrievalResponseAssembler);
        ContextFileChatSupport fileSupport = new ContextFileChatSupport(fileService,
                new MetaContextFileAdvisor(fileService), chatScopeResolver);
        return new ChatMessageService(chatClient, chatScopeResolver, chatHistoryRecorder,
                streamEventAssembler, fileSupport);
    }

    private KnowledgeChatService knowledgeChatService(ChatModel chatModel,
                                                      MetaChatHistoryService metaChatHistoryService,
                                                      MetaChatService metaChatService,
                                                      RetrievalDecisionService decisionService) {
        MetaChatFileService fileService = mock(MetaChatFileService.class);
        return knowledgeChatService(chatModel, metaChatHistoryService, metaChatService, decisionService, fileService);
    }

    private KnowledgeChatService knowledgeChatService(ChatModel chatModel,
                                                      MetaChatHistoryService metaChatHistoryService,
                                                      MetaChatService metaChatService,
                                                      RetrievalDecisionService decisionService,
                                                      MetaChatFileService fileService) {
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        ChatScopeResolver chatScopeResolver = new ChatScopeResolver();
        ChatHistoryRecorder chatHistoryRecorder = new ChatHistoryRecorder(metaChatService, metaChatHistoryService,
                chatScopeResolver);
        RetrievalResponseAssembler retrievalResponseAssembler = new RetrievalResponseAssembler();
        ChatStreamEventAssembler streamEventAssembler = new ChatStreamEventAssembler(chatHistoryRecorder,
                retrievalResponseAssembler);
        ContextFileChatSupport fileSupport = new ContextFileChatSupport(fileService,
                new MetaContextFileAdvisor(fileService), chatScopeResolver);
        return new KnowledgeChatService(chatClient, chatModel, mock(VectorStore.class),
                mock(RetrievalAdvisorFactory.class), mock(RetrievalFilterExpressionFactory.class),
                retrievalResponseAssembler, decisionService, new RetrievalOptionsFactory(), chatScopeResolver,
                chatHistoryRecorder, streamEventAssembler, fileSupport);
    }

    private MetaChatService metaChatService() {
        MetaChatService metaChatService = mock(MetaChatService.class);
        MetaChatDO chat = new MetaChatDO();
        chat.setId(1L);
        when(metaChatService.getOrCreate(any())).thenReturn(chat);
        return metaChatService;
    }

    private ChatRequest chatRequest(String chatId, String tenantId, String userId, String msg) {
        return chatRequest(chatId, tenantId, userId, msg, null);
    }

    private ChatRequest chatRequest(String chatId,
                                    String tenantId,
                                    String userId,
                                    String msg,
                                    List<String> fileIds) {
        ChatRequest request = new ChatRequest();
        request.setChatId(chatId);
        request.setTenantId(tenantId);
        request.setUserId(userId);
        request.setMsg(msg);
        request.setFileIds(fileIds);
        return request;
    }

    private RetrievalChatRequest retrievalChatRequest(String chatId, String msg, String tenantId, String kbId) {
        return retrievalChatRequest(chatId, msg, tenantId, kbId, null, null);
    }

    private RetrievalChatRequest retrievalChatRequest(String chatId,
                                                      String msg,
                                                      String tenantId,
                                                      String kbId,
                                                      String userId,
                                                      List<String> fileIds) {
        RetrievalChatRequest request = new RetrievalChatRequest();
        request.setChatId(chatId);
        request.setMsg(msg);
        request.setTenantId(tenantId);
        request.setKbId(kbId);
        request.setUserId(userId);
        request.setFileIds(fileIds);
        return request;
    }

    private MetaContextFile file(String fileId, String filename, String documentType) {
        return new MetaContextFile(fileId, filename, documentType);
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
