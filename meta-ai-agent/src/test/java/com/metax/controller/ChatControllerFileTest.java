package com.metax.controller;

import com.metax.chat.file.ChatFileResponse;
import com.metax.chat.file.ChatFileService;
import com.metax.history.ChatHistoryService;
import com.metax.history.ChatHistoryRole;
import com.metax.history.ChatHistoryType;
import com.metax.history.MetaChatDO;
import com.metax.history.MetaChatService;
import com.metax.rag.indexing.DocumentIndexingService;
import com.metax.rag.model.MetadataKeys;
import com.metax.rag.retrieval.RetrievalAdvisorFactory;
import com.metax.rag.retrieval.RetrievalDecisionResult;
import com.metax.rag.retrieval.RetrievalDecisionService;
import com.metax.rag.retrieval.RetrievalFilterExpressionFactory;
import com.metax.rag.retrieval.RetrievalChatResponse;
import com.metax.rag.retrieval.RetrievalResponseAssembler;
import com.metax.rag.retrieval.RetrievalSearchService;
import com.metax.rag.retrieval.MetaContextFile;
import com.metax.rag.retrieval.MetaContextFileAdvisor;
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
 * ChatControllerFileTest .
 *
 * <p>
 * ChatController 聊天文件接口单元测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
class ChatControllerFileTest {

    @Test
    void chatShouldAnswerWithConversationFiles() {
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        MetaChatService metaChatService = metaChatService();
        ChatFileService fileService = mock(ChatFileService.class);
        MetaContextFile file = file("file-1", "demo.pdf", "pdf");
        when(fileService.uploadAndIndex(eq("t1"), eq("u1"), eq("c1"), any())).thenReturn(List.of());
        when(fileService.readyFiles("t1", "u1", "c1")).thenReturn(List.of(file));
        when(fileService.retrieve(eq("t1"), eq("u1"), eq("c1"), eq(List.of(file)), eq("总结一下")))
                .thenReturn(List.of(Document.builder()
                        .text("文件内容")
                        .metadata(Map.of(MetadataKeys.FILE_NAME, "demo.pdf", MetadataKeys.CHUNK_INDEX, 0))
                        .build()));
        ChatController controller = controller(new TestChatModel("基于文件的回答"), chatHistoryService,
                metaChatService, fileService);

        ChatFileResponse response = controller.chatWithFiles("c1", "t1", "u1", "总结一下", null);

        assertThat(response.answer()).isEqualTo("基于文件的回答");
        assertThat(response.conversationId()).isEqualTo("c1");
        assertThat(response.files()).hasSize(1);
        assertThat(response.files().get(0).fileId()).isEqualTo("file-1");
        verify(chatHistoryService).saveUserMessage(1L, "c1", ChatHistoryType.FILE_CHAT, "总结一下");
        verify(chatHistoryService).saveAssistantMessage(1L, "c1", ChatHistoryType.FILE_CHAT, "基于文件的回答");
        verify(metaChatService).updateLastMessage(1L, ChatHistoryRole.USER, "总结一下");
        verify(metaChatService).updateLastMessage(1L, ChatHistoryRole.ASSISTANT, "基于文件的回答");
    }

    @Test
    void ragShouldAnswerWithConversationFilesWhenRetrievalSkipped() {
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        MetaChatService metaChatService = metaChatService();
        ChatFileService fileService = mock(ChatFileService.class);
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
        ChatController controller = controller(new TestChatModel("对比回答"), chatHistoryService,
                metaChatService, fileService, decisionService);

        RetrievalChatResponse response = controller.ragWithFiles("c1", "对比一下", "t1", "kb1",
                null, null, "u1", null, null);

        assertThat(response.answer()).isEqualTo("对比回答");
        assertThat(response.conversationId()).isEqualTo("c1");
        assertThat(response.references()).isEmpty();
        assertThat(response.files()).hasSize(1);
        assertThat(response.files().get(0).fileId()).isEqualTo("file-1");
    }

    private ChatController controller(ChatModel chatModel,
                                      ChatHistoryService chatHistoryService,
                                      MetaChatService metaChatService,
                                      ChatFileService fileService) {
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        return new ChatController(chatClient, chatClient, chatModel, mock(VectorStore.class),
                mock(DocumentIndexingService.class), mock(RetrievalAdvisorFactory.class),
                mock(RetrievalFilterExpressionFactory.class), new RetrievalResponseAssembler(),
                mock(RetrievalSearchService.class), mock(RetrievalDecisionService.class), chatHistoryService,
                metaChatService, fileService, new MetaContextFileAdvisor(fileService));
    }

    private ChatController controller(ChatModel chatModel,
                                      ChatHistoryService chatHistoryService,
                                      MetaChatService metaChatService,
                                      ChatFileService fileService,
                                      RetrievalDecisionService decisionService) {
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        return new ChatController(chatClient, chatClient, chatModel, mock(VectorStore.class),
                mock(DocumentIndexingService.class), mock(RetrievalAdvisorFactory.class),
                mock(RetrievalFilterExpressionFactory.class), new RetrievalResponseAssembler(),
                mock(RetrievalSearchService.class), decisionService, chatHistoryService,
                metaChatService, fileService, new MetaContextFileAdvisor(fileService));
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
