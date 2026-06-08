package com.metax.controller;

import com.metax.chat.ChatMessageService;
import com.metax.chat.file.MetaChatFileResponse;
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
import com.metax.chat.request.ChatFileRequest;
import com.metax.rag.model.MetadataKeys;
import com.metax.retrieval.chat.KnowledgeChatService;
import com.metax.rag.retrieval.advisor.MetaContextFile;
import com.metax.rag.retrieval.advisor.MetaContextFileAdvisor;
import com.metax.rag.retrieval.advisor.RetrievalAdvisorFactory;
import com.metax.rag.retrieval.assembly.RetrievalResponseAssembler;
import com.metax.rag.retrieval.decision.RetrievalDecisionResult;
import com.metax.rag.retrieval.decision.RetrievalDecisionService;
import com.metax.rag.retrieval.filter.RetrievalFilterExpressionFactory;
import com.metax.rag.retrieval.model.RetrievalChatResponse;
import com.metax.retrieval.chat.request.RetrievalChatFileRequest;
import com.metax.retrieval.chat.RetrievalOptionsFactory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatApplicationFileTest .
 *
 * <p>
 * 聊天文件应用服务单元测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
class ChatApplicationFileTest {

    @Test
    void chatShouldAnswerWithChatFiles() {
        MetaChatHistoryService metaChatHistoryService = mock(MetaChatHistoryService.class);
        MetaChatService metaChatService = metaChatService();
        MetaChatFileService fileService = mock(MetaChatFileService.class);
        MetaContextFile file = file("file-1", "demo.pdf", "pdf");
        when(fileService.uploadAndIndex(eq("t1"), eq("u1"), eq("c1"), any())).thenReturn(List.of());
        when(fileService.readyFiles("t1", "u1", "c1")).thenReturn(List.of(file));
        when(fileService.retrieve(eq("t1"), eq("u1"), eq("c1"), eq(List.of(file)), eq("总结一下")))
                .thenReturn(List.of(Document.builder()
                        .text("文件内容")
                        .metadata(Map.of(MetadataKeys.FILE_NAME, "demo.pdf", MetadataKeys.CHUNK_INDEX, 0))
                        .build()));
        ChatMessageService service = chatMessageService(new TestChatModel("基于文件的回答"),
                metaChatHistoryService, metaChatService, fileService);

        MetaChatFileResponse response = service.chatWithFiles(chatFileRequest("c1", "t1", "u1", "总结一下"));

        assertThat(response.answer()).isEqualTo("基于文件的回答");
        assertThat(response.chatId()).isEqualTo("c1");
        assertThat(response.files()).hasSize(1);
        assertThat(response.files().get(0).fileId()).isEqualTo("file-1");
        verify(metaChatHistoryService).saveUserMessage(1L, "c1", MetaChatHistoryType.FILE_CHAT, "总结一下");
        verify(metaChatHistoryService).saveAssistantMessage(1L, "c1", MetaChatHistoryType.FILE_CHAT, "基于文件的回答");
        verify(metaChatService).updateLastMessage(1L, MetaChatHistoryRole.USER, "总结一下");
        verify(metaChatService).updateLastMessage(1L, MetaChatHistoryRole.ASSISTANT, "基于文件的回答");
    }

    @Test
    void ragShouldAnswerWithChatFilesWhenRetrievalSkipped() {
        MetaChatHistoryService metaChatHistoryService = mock(MetaChatHistoryService.class);
        MetaChatService metaChatService = metaChatService();
        MetaChatFileService fileService = mock(MetaChatFileService.class);
        RetrievalDecisionService decisionService = mock(RetrievalDecisionService.class);
        MetaContextFile file = file("file-1", "demo.pdf", "pdf");
        when(decisionService.decide(any())).thenReturn(RetrievalDecisionResult.skip("skip_pattern"));
        when(fileService.uploadAndIndex(eq("t1"), eq("u1"), eq("c1"), any())).thenReturn(List.of());
        when(fileService.readyFiles("t1", "u1", "c1")).thenReturn(List.of(file));
        when(fileService.retrieve(eq("t1"), eq("u1"), eq("c1"), eq(List.of(file)), eq("对比一下")))
                .thenReturn(List.of(Document.builder()
                        .text("上传文件内容")
                        .metadata(Map.of(MetadataKeys.FILE_NAME, "demo.pdf", MetadataKeys.CHUNK_INDEX, 0))
                        .build()));
        KnowledgeChatService service = knowledgeChatService(new TestChatModel("对比回答"),
                metaChatHistoryService, metaChatService, fileService, decisionService);

        RetrievalChatResponse response = service.chatWithFiles(retrievalChatFileRequest("c1", "对比一下", "t1",
                "kb1", "u1"));

        assertThat(response.answer()).isEqualTo("对比回答");
        assertThat(response.chatId()).isEqualTo("c1");
        assertThat(response.references()).isEmpty();
        assertThat(response.files()).hasSize(1);
        assertThat(response.files().get(0).fileId()).isEqualTo("file-1");
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
                                                      MetaChatFileService fileService,
                                                      RetrievalDecisionService decisionService) {
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

    private MetaContextFile file(String fileId, String filename, String documentType) {
        return new MetaContextFile(fileId, filename, documentType);
    }

    private ChatFileRequest chatFileRequest(String chatId, String tenantId, String userId, String msg) {
        ChatFileRequest request = new ChatFileRequest();
        request.setChatId(chatId);
        request.setTenantId(tenantId);
        request.setUserId(userId);
        request.setMsg(msg);
        return request;
    }

    private RetrievalChatFileRequest retrievalChatFileRequest(String chatId,
                                                              String msg,
                                                              String tenantId,
                                                              String kbId,
                                                              String userId) {
        RetrievalChatFileRequest request = new RetrievalChatFileRequest();
        request.setChatId(chatId);
        request.setMsg(msg);
        request.setTenantId(tenantId);
        request.setKbId(kbId);
        request.setUserId(userId);
        return request;
    }

    private static final class TestChatModel implements ChatModel {

        private final String answer;

        private TestChatModel(String answer) {
            this.answer = answer;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
        }
    }
}
