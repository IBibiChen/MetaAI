package com.metax.rag.retrieval;

import com.metax.rag.model.MetadataKeys;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RetrievalResponseAssemblerTest .
 *
 * <p>
 * RAG 响应组装器单元测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/4
 */
class RetrievalResponseAssemblerTest {

    private final RetrievalResponseAssembler assembler = new RetrievalResponseAssembler();

    @Test
    void chatShouldReturnDeduplicatedFileCitations() {
        ChatClientResponse response = response(document("chunk-1", "doc1", "demo.docx"),
                document("chunk-2", "doc1", "demo.docx"),
                document("chunk-3", "doc2", "demo.docx"));

        RetrievalChatResponse chatResponse = assembler.chat(response, "c1");

        assertThat(chatResponse.answer()).isEqualTo("answer");
        assertThat(chatResponse.conversationId()).isEqualTo("c1");
        assertThat(chatResponse.references()).containsExactly(
                new RetrievalCitation("demo.docx", "doc1"),
                new RetrievalCitation("demo.docx", "doc2"));
    }

    @Test
    void chatShouldSkipCitationWhenRequiredMetadataMissing() {
        ChatClientResponse response = response(Document.builder()
                .text("chunk")
                .metadata(Map.of(MetadataKeys.FILENAME, "demo.docx",
                        MetadataKeys.TENANT_ID, "t1",
                        MetadataKeys.KNOWLEDGE_BASE_ID, "kb1"))
                .build());

        RetrievalChatResponse chatResponse = assembler.chat(response, "c1");

        assertThat(chatResponse.references()).isEmpty();
    }

    @Test
    void detailsShouldKeepFullReferences() {
        RetrievalTrace.Builder traceBuilder = RetrievalTrace.builder("query");
        ChatClientResponse response = responseWithContext(Map.of(RetrievalTrace.CONTEXT_KEY, traceBuilder),
                document("chunk-1", "doc1", "demo.docx"),
                document("chunk-2", "doc1", "demo.docx"));

        RetrievalChatDetailsResponse detailsResponse = assembler.details(response, "c1");

        assertThat(detailsResponse.answer()).isEqualTo("answer");
        assertThat(detailsResponse.conversationId()).isEqualTo("c1");
        assertThat(detailsResponse.references()).hasSize(2);
        assertThat(detailsResponse.references().get(0).text()).isEqualTo("chunk-1");
        assertThat(detailsResponse.references().get(0).metadata()).containsEntry(MetadataKeys.DOCUMENT_ID, "doc1");
        assertThat(detailsResponse.trace()).isNotNull();
    }

    @Test
    void chatWithoutReferencesShouldReturnEmptyReferences() {
        ChatClientResponse response = response(document("chunk-1", "doc1", "demo.docx"));

        RetrievalChatResponse chatResponse = assembler.chatWithoutReferences(response, "c1");

        assertThat(chatResponse.answer()).isEqualTo("answer");
        assertThat(chatResponse.conversationId()).isEqualTo("c1");
        assertThat(chatResponse.references()).isEmpty();
    }

    @Test
    void streamChatShouldReadCitationsFromContextWhenChatResponseMetadataMissing() {
        ChatClientResponse response = ChatClientResponse.builder()
                .chatResponse(ChatResponse.builder()
                        .generations(List.of(new Generation(new AssistantMessage("chunk"))))
                        .build())
                .context(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT,
                        List.of(document("chunk-1", "doc1", "demo.docx"),
                                document("chunk-2", "doc1", "demo.docx")))
                .build();

        RetrievalChatResponse chatResponse = assembler.streamChat("answer", response, "c1");

        assertThat(chatResponse.answer()).isEqualTo("answer");
        assertThat(chatResponse.references()).containsExactly(new RetrievalCitation("demo.docx", "doc1"));
    }

    private ChatClientResponse response(Document... documents) {
        return responseWithContext(Map.of(), documents);
    }

    private ChatClientResponse responseWithContext(Map<String, Object> context, Document... documents) {
        ChatResponse chatResponse = ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage("answer"))))
                .metadata(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT, List.of(documents))
                .build();
        return ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .context(context)
                .build();
    }

    private Document document(String text, String documentId, String filename) {
        return Document.builder()
                .text(text)
                .metadata(Map.of(MetadataKeys.TENANT_ID, "t1",
                        MetadataKeys.KNOWLEDGE_BASE_ID, "kb1",
                        MetadataKeys.DOCUMENT_ID, documentId,
                        MetadataKeys.FILENAME, filename,
                        MetadataKeys.SOURCE, "storage/t1/kb1/%s".formatted(filename)))
                .score(0.9)
                .build();
    }
}
