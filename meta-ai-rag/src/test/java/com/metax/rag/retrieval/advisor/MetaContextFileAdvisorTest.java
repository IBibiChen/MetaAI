package com.metax.rag.retrieval.advisor;

import com.metax.rag.model.MetadataKeys;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MetaContextFileAdvisorTest .
 *
 * <p>
 * 会话级文件上下文 Advisor 单元测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
class MetaContextFileAdvisorTest {

    @Test
    void shouldUseOriginalUserQueryAndExposeFiles() {
        MetaContextFileService contextFileService = mock(MetaContextFileService.class);
        MetaContextFile file = new MetaContextFile("file-1", "demo.pdf", "pdf");
        when(contextFileService.retrieve("t1", "u1", "c1", List.of(file), "原始问题"))
                .thenReturn(List.of(Document.builder()
                        .text("文件内容")
                        .metadata(Map.of(MetadataKeys.FILE_NAME, "demo.pdf", MetadataKeys.CHUNK_INDEX, 0))
                        .build()));
        AtomicReference<String> promptText = new AtomicReference<>();
        ChatClient chatClient = ChatClient.builder(new TestChatModel(promptText)).build();

        ChatClientResponse response = chatClient.prompt()
                .advisors(spec -> {
                    spec.param(MetaContextFileKeys.TENANT_ID, "t1");
                    spec.param(MetaContextFileKeys.USER_ID, "u1");
                    spec.param(MetaContextFileKeys.CHAT_ID, "c1");
                    spec.param(MetaContextFileKeys.ORIGINAL_USER_QUERY, "原始问题");
                    spec.param(MetaContextFileKeys.INCOMING_FILES, List.of(file));
                    spec.advisors(new MetaContextFileAdvisor(contextFileService));
                })
                .user("被增强后的问题")
                .call()
                .chatClientResponse();

        verify(contextFileService).retrieve("t1", "u1", "c1", List.of(file), "原始问题");
        assertThat(promptText.get()).contains("当前会话上传文件上下文").contains("文件内容");
        Object files = response.chatResponse().getMetadata().get(MetaContextFileKeys.CONTEXT_FILES);
        assertThat(files).isEqualTo(List.of(file));
    }

    private static final class TestChatModel implements ChatModel {

        private final AtomicReference<String> promptText;

        private TestChatModel(AtomicReference<String> promptText) {
            this.promptText = promptText;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            promptText.set(prompt.getUserMessage().getText());
            return new ChatResponse(List.of(new Generation(new AssistantMessage("answer"))));
        }
    }
}
