package com.metax.rag.retrieval;

import com.metax.rag.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RetrievalDecisionServiceTest .
 *
 * <p>
 * 普通 RAG 对话检索门控单元测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/4
 */
class RetrievalDecisionServiceTest {

    @Test
    void shouldSkipAssistantIdentityQuestionByRule() {
        TestChatModel chatModel = new TestChatModel("RETRIEVE");
        RetrievalDecisionService service = new RetrievalDecisionService(properties("hybrid"), chatModel);

        RetrievalDecisionResult result = service.decide(options("你是谁"));

        assertThat(result.decision()).isEqualTo(RetrievalDecision.SKIP);
        assertThat(result.reason()).isEqualTo("skip_pattern");
        assertThat(chatModel.called).isFalse();
    }

    @Test
    void shouldRetrieveKnowledgeQuestionByRule() {
        TestChatModel chatModel = new TestChatModel("SKIP");
        RetrievalDecisionService service = new RetrievalDecisionService(properties("hybrid"), chatModel);

        RetrievalDecisionResult result = service.decide(options("根据知识库总结一下项目方案"));

        assertThat(result.decision()).isEqualTo(RetrievalDecision.RETRIEVE);
        assertThat(result.reason()).isEqualTo("retrieve_keyword");
        assertThat(chatModel.called).isFalse();
    }

    @Test
    void shouldRetrieveWhenDocumentScopePresent() {
        TestChatModel chatModel = new TestChatModel("SKIP");
        RetrievalDecisionService service = new RetrievalDecisionService(properties("hybrid"), chatModel);

        RetrievalDecisionResult result = service.decide(RetrievalOptions.builder()
                .tenantId("t1")
                .knowledgeBaseId("kb1")
                .documentId("doc1")
                .query("这个怎么处理")
                .build());

        assertThat(result.decision()).isEqualTo(RetrievalDecision.RETRIEVE);
        assertThat(result.reason()).isEqualTo("document_id_present");
        assertThat(chatModel.called).isFalse();
    }

    @Test
    void shouldCallLlmForUnknownQuestionInHybridMode() {
        TestChatModel chatModel = new TestChatModel("SKIP");
        RetrievalDecisionService service = new RetrievalDecisionService(properties("hybrid"), chatModel);

        RetrievalDecisionResult result = service.decide(options("继续说"));

        assertThat(result.decision()).isEqualTo(RetrievalDecision.SKIP);
        assertThat(result.reason()).isEqualTo("llm_skip");
        assertThat(chatModel.called).isTrue();
    }

    @Test
    void shouldDefaultRetrieveWhenLlmReturnsInvalidAnswer() {
        TestChatModel chatModel = new TestChatModel("UNKNOWN");
        RetrievalDecisionService service = new RetrievalDecisionService(properties("hybrid"), chatModel);

        RetrievalDecisionResult result = service.decide(options("继续说"));

        assertThat(result.decision()).isEqualTo(RetrievalDecision.RETRIEVE);
        assertThat(result.reason()).isEqualTo("llm_invalid_default_retrieve");
    }

    @Test
    void shouldDefaultRetrieveWhenLlmFails() {
        TestChatModel chatModel = new TestChatModel("RETRIEVE");
        chatModel.throwError = true;
        RetrievalDecisionService service = new RetrievalDecisionService(properties("hybrid"), chatModel);

        RetrievalDecisionResult result = service.decide(options("继续说"));

        assertThat(result.decision()).isEqualTo(RetrievalDecision.RETRIEVE);
        assertThat(result.reason()).isEqualTo("llm_error_default_retrieve");
    }

    private RagProperties properties(String mode) {
        RagProperties properties = new RagProperties();
        properties.getRetrieval().getDecision().setMode(mode);
        return properties;
    }

    private RetrievalOptions options(String query) {
        return RetrievalOptions.builder()
                .tenantId("t1")
                .knowledgeBaseId("kb1")
                .query(query)
                .build();
    }

    private static class TestChatModel implements ChatModel {

        private final String answer;

        private boolean called;

        private boolean throwError;

        TestChatModel(String answer) {
            this.answer = answer;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            this.called = true;
            if (throwError) {
                throw new IllegalStateException("model error");
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
        }
    }
}
