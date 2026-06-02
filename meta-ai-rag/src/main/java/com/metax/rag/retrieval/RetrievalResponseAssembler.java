package com.metax.rag.retrieval;

import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RetrievalResponseAssembler .
 *
 * <p>
 * RAG 响应组装器，从 ChatClientResponse 中提取最终回答和 RetrievalAugmentationAdvisor 保存的命中文档上下文
 *
 * <p>
 * 设计说明：为什么 details 接口需要 ChatClientResponse
 * 普通 content() 只能拿到模型回答文本，拿不到检索命中的 Document
 * RetrievalAugmentationAdvisor 会把命中文档放入 ChatResponse metadata，key 是 rag_document_context
 * 所以 details 接口必须使用 chatClientResponse() 才能同时返回 answer 和 references
 *
 * <p>
 * 返回结构示例
 * <pre>{@code
 * {
 *   "answer": "Spring AI ETL 由 Reader、Transformer、Writer 组成",
 *   "conversationId": "tenantId:userId:sessionId",
 *   "references": [
 *     {
 *       "score": 0.82,
 *       "metadata": {
 *         "documentId": "doc-001",
 *         "chunkId": "doc-001:0",
 *         "source": "knowledge/t1/kb1/demo.md"
 *       }
 *     }
 *   ]
 * }
 * }</pre>
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
@Component
public class RetrievalResponseAssembler {

    /**
     * 组装 RAG 详情响应
     *
     * <p>
     * answer 来自模型最终输出
     * references 来自 RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT
     *
     * @param response       ChatClientResponse
     * @param conversationId 会话 ID
     * @return RAG 详情响应
     */
    public RetrievalChatResponse details(ChatClientResponse response, String conversationId) {
        // 阶段 1：读取模型最终回答，answer 是 ChatModel 生成后的文本
        // ChatResponse 可能为空，details 接口要优先保证响应结构稳定
        String answer = response.chatResponse() == null ? null : response.chatResponse().getResult().getOutput().getText();

        // 阶段 2：组装 answer、references 和 trace，供 details 接口排查完整检索链路
        return new RetrievalChatResponse(answer, conversationId, references(response), trace(response));
    }

    private RetrievalTrace trace(ChatClientResponse response) {
        // trace 来自 advisor context 中同一个 RetrievalTrace.Builder
        // QueryTransformer 和 DocumentPostProcessor 会在执行过程中把数据追加到 builder
        // Controller 放入的 builder 会随 ChatClientResponse context 返回，这里统一转成只读快照
        Object value = response.context().get(RetrievalTrace.CONTEXT_KEY);
        if (value instanceof RetrievalTrace.Builder builder) {
            return builder.build();
        }
        return null;
    }

    private List<RetrievalReference> references(ChatClientResponse response) {
        // references 来自 RetrievalAugmentationAdvisor 保存的最终上下文 Document
        // 它不是 VectorStore 原始候选全集，而是后处理后实际参与 prompt 增强的文档
        // 没有 ChatResponse 时直接返回空引用，避免 details 接口因为模型异常产生空指针
        if (response.chatResponse() == null) {
            return List.of();
        }
        // RetrievalAugmentationAdvisor.after 会把检索到的 Document 写入 ChatResponse metadata
        // 这里读取的是最终参与 prompt 增强的 Document，不是向量库中的全部候选结果
        Object documents = response.chatResponse().getMetadata().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
        if (!(documents instanceof List<?> list)) {
            return List.of();
        }
        // 只保留真正的 Document，避免 metadata 中出现非预期对象时影响接口稳定性
        // RetrievalReference 保留文本、分数和 metadata，供前端展示来源和排查召回质量
        return list.stream()
                .filter(Document.class::isInstance)
                .map(Document.class::cast)
                .map(document -> new RetrievalReference(document.getText(), document.getScore(), document.getMetadata()))
                .toList();
    }
}
