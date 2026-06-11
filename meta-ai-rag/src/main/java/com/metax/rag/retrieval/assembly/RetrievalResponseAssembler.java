package com.metax.rag.retrieval.assembly;

import com.metax.rag.config.MetaRetrievalProperties;
import com.metax.rag.model.MetadataKeys;
import com.metax.rag.retrieval.advisor.MetaContextFile;
import com.metax.rag.retrieval.advisor.MetaContextFileKeys;
import com.metax.rag.retrieval.model.RetrievalChatDetailsResponse;
import com.metax.rag.retrieval.model.RetrievalChatResponse;
import com.metax.rag.retrieval.model.RetrievalChunkReference;
import com.metax.rag.retrieval.model.RetrievalDocumentReference;
import com.metax.rag.retrieval.trace.RetrievalTrace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * RetrievalResponseAssembler .
 *
 * <p>
 * RAG 响应组装器，从 ChatClientResponse 中提取最终回答和 RetrievalAugmentationAdvisor 保存的命中文档上下文
 *
 * <p>
 * 设计说明：为什么 details 接口需要 ChatClientResponse
 * ChatClient.content() 只能拿到模型回答文本，拿不到检索命中的 Document
 * RetrievalAugmentationAdvisor 会把命中文档放入 ChatResponse metadata，key 是 rag_document_context
 * 所以 RAG 响应组装必须使用 chatClientResponse() 才能同时返回 answer 和 references
 *
 * <p>
 * 返回结构示例
 * <pre>{@code
 * {
 *   "answer": "Spring AI ETL 由 Reader、Transformer、Writer 组成",
 *   "chatId": "tenantId-userId-sessionId",
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
@Slf4j
public class RetrievalResponseAssembler {

    private final MetaRetrievalProperties.Reference referenceProperties;

    public RetrievalResponseAssembler() {
        this(new MetaRetrievalProperties.Reference());
    }

    @Autowired
    public RetrievalResponseAssembler(MetaRetrievalProperties properties) {
        this(properties.getReference());
    }

    private RetrievalResponseAssembler(MetaRetrievalProperties.Reference referenceProperties) {
        this.referenceProperties = referenceProperties;
    }

    /**
     * 组装 RAG 详情响应
     *
     * <p>
     * answer 来自模型最终输出
     * references 来自 RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT
     *
     * @param response ChatClientResponse
     * @param chatId   会话 ID
     * @return RAG 详情响应
     */
    public RetrievalChatDetailsResponse details(ChatClientResponse response, String chatId) {
        // 阶段 1：读取模型最终回答，answer 是 ChatModel 生成后的文本
        // ChatResponse 可能为空，details 接口要优先保证响应结构稳定
        String answer = answer(response);

        // 阶段 2：组装 answer、references 和 trace，供 details 接口排查完整检索链路
        return new RetrievalChatDetailsResponse(answer, chatId, chunkReferences(response), trace(response));
    }

    /**
     * 组装 RAG 普通响应
     *
     * <p>
     * 普通接口返回 answer 和轻量 references，避免把 chunk 文本、score 和 metadata 暴露给前端常规调用
     *
     * @param response ChatClientResponse
     * @param chatId   会话 ID
     * @return RAG 普通响应
     */
    public RetrievalChatResponse chat(ChatClientResponse response, String chatId) {
        return new RetrievalChatResponse(answer(response), chatId, documentReferences(response), files(response));
    }

    /**
     * 组装不带引用的 RAG 普通响应
     *
     * <p>
     * 当前轮被检索决策判定为无需知识库时使用
     * 该响应仍保留 RAG ChatClient 的系统提示词和 ChatMemory，但不会暴露任何历史或检索引用
     *
     * @param response ChatClientResponse
     * @param chatId   会话 ID
     * @return RAG 普通响应
     */
    public RetrievalChatResponse chatWithoutReferences(ChatClientResponse response, String chatId) {
        return new RetrievalChatResponse(answer(response), chatId, List.of(), files(response));
    }

    /**
     * 组装 RAG 流式完成响应
     *
     * <p>
     * 流式输出的完整 answer 由调用方聚合，references 仍从 RetrievalAugmentationAdvisor 保存的上下文读取
     *
     * @param answer   完整回答
     * @param response 最后一个流式 ChatClientResponse
     * @param chatId   会话 ID
     * @return RAG 普通响应
     */
    public RetrievalChatResponse streamResponse(String answer, ChatClientResponse response, String chatId) {
        return new RetrievalChatResponse(answer, chatId, documentReferences(response), files(response));
    }

    @SuppressWarnings("unchecked")
    private List<MetaContextFile> files(ChatClientResponse response) {
        Object value = null;
        if (response.chatResponse() != null) {
            value = response.chatResponse().getMetadata().get(MetaContextFileKeys.CONTEXT_FILES);
        }
        if (value == null) {
            value = response.context().get(MetaContextFileKeys.CONTEXT_FILES);
        }
        if (!(value instanceof List<?> list) || !list.stream().allMatch(MetaContextFile.class::isInstance)) {
            return List.of();
        }
        return (List<MetaContextFile>) list;
    }

    private String answer(ChatClientResponse response) {
        return response.chatResponse() == null ? null : response.chatResponse().getResult().getOutput().getText();
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

    private List<RetrievalChunkReference> chunkReferences(ChatClientResponse response) {
        // references 来自 RetrievalAugmentationAdvisor 保存的最终上下文 Document
        // 它不是 VectorStore 原始候选全集，而是后处理后实际参与 prompt 增强的文档
        // 只保留真正的 Document，避免 metadata 中出现非预期对象时影响接口稳定性
        // RetrievalChunkReference 保留文本、分数和 metadata，供前端展示来源和排查召回质量
        return documents(response).stream()
                .map(document -> new RetrievalChunkReference(document.getText(), document.getScore(),
                        document.getMetadata()))
                .toList();
    }

    /**
     * 组装普通 RAG 对话的轻量文件引用
     *
     * <p>
     * 普通 /v1/rag 只面向前端聊天窗口展示文件来源，不暴露 chunk 文本、score 和完整 metadata
     * 多个 chunk 命中同一个文件时按 documentId 去重，保留首次出现顺序
     * 只处理 scope = knowledge 的知识库文档，会话文件上下文由 files 字段单独承载
     * references 有单独准入阈值，不再等同于所有进入 prompt 的上下文文件
     *
     * @param response ChatClientResponse
     * @return 轻量文件引用列表
     */
    private List<RetrievalDocumentReference> documentReferences(ChatClientResponse response) {
        // 只读取最终进入 prompt 的上下文 Document，不读取 VectorStore 原始候选全集
        // 普通 references 是用户可见来源，需要在上下文基础上再做引用准入
        List<Document> contextDocuments = documents(response);
        Map<String, ReferenceCandidate> candidates = new LinkedHashMap<>();
        for (Document document : contextDocuments) {
            // 单个 chunk 先转为文件级候选，非知识库 scope 或缺少关键 metadata 会被跳过
            ReferenceCandidate candidate = referenceCandidate(document);
            if (candidate != null) {
                // 同一 documentId 的多个 chunk 合并成一个引用候选
                // bestScore 保留最高分，chunkCount 记录该文件被上下文命中的次数
                candidates.compute(candidate.reference().documentId(), (documentId, existing) ->
                        existing == null ? candidate : existing.merge(candidate));
            }
        }
        // 引用展示比 prompt 上下文更严格
        // 先按配置准入过滤，再按文件最高分排序，最后限制前端展示数量
        List<RetrievalDocumentReference> references = candidates.values().stream()
                .filter(this::allowedReference)
                .sorted(Comparator.comparingDouble(ReferenceCandidate::bestScore).reversed())
                .limit(Math.max(0, referenceProperties.getMaxReferences()))
                .map(ReferenceCandidate::reference)
                .toList();
        // 只记录数量变化，不打印 chunk 正文或完整 metadata，避免日志污染
        int filteredCount = candidates.size() - references.size();
        log.debug("RAG 引用组装完成：contextDocumentCount = {}，candidateReferenceCount = {}，"
                        + "referenceCount = {}，filteredReferenceCount = {}，minReferenceScore = {}",
                contextDocuments.size(), candidates.size(), references.size(), filteredCount,
                referenceProperties.getMinReferenceScore());
        return references;
    }

    /**
     * 从 Document 组装单个引用候选
     *
     * <p>
     * documentName 用于前端展示，documentId 用于前端调用业务下载接口
     * 缺少任一关键字段时跳过该引用，避免前端拿到不可展示或不可下载的来源
     * scope 不是 knowledge 时跳过，避免把会话级 fileId 伪装成知识库 documentId
     * 缺少 score 时按 0 处理，普通 references 应比 prompt 上下文更严格
     *
     * @param document 上下文文档
     * @return 引用候选
     */
    private ReferenceCandidate referenceCandidate(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        // references 只展示知识库来源，会话文件上下文由 files 字段单独返回
        String scope = metadataValue(metadata, MetadataKeys.SCOPE);
        if (!MetadataKeys.SCOPE_KNOWLEDGE.equals(scope)) {
            return null;
        }
        // documentName 用于前端展示，documentId 用于业务下载和文件级去重
        // 缺任一字段都不能形成可靠引用
        String documentName = metadataValue(metadata, MetadataKeys.DOCUMENT_NAME);
        String documentId = metadataValue(metadata, MetadataKeys.DOCUMENT_ID);
        if (!StringUtils.hasText(documentName) || !StringUtils.hasText(documentId)) {
            return null;
        }
        // 单个 chunk 先作为命中次数为 1 的文件级引用候选
        // 缺失 score 时 scoreOrZero 返回 0，普通 references 默认不会展示低可信候选
        return new ReferenceCandidate(new RetrievalDocumentReference(documentId, documentName),
                scoreOrZero(document), 1);
    }

    /**
     * 判断引用候选是否允许展示
     *
     * <p>
     * 普通 references 是用户可见来源，应使用比上下文更严格的文件级准入
     *
     * @param candidate 引用候选
     * @return true 表示允许展示
     */
    private boolean allowedReference(ReferenceCandidate candidate) {
        return candidate.bestScore() >= referenceProperties.getMinReferenceScore()
                && candidate.chunkCount() >= referenceProperties.getMinChunksPerReference();
    }

    /**
     * 将缺失 score 的 Document 作为 0 分处理
     *
     * @param document 文档
     * @return 可排序分数
     */
    private double scoreOrZero(Document document) {
        return document.getScore() == null ? 0.0 : document.getScore();
    }

    /**
     * 安全读取 metadata 字段
     *
     * <p>
     * 向量库返回的 metadata value 可能是 String、Number 或其他对象，这里统一转成字符串供响应组装使用
     *
     * @param metadata Document metadata
     * @param key      metadata key
     * @return 字符串字段值
     */
    private String metadataValue(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 提取 RetrievalAugmentationAdvisor 保存的最终上下文 Document
     *
     * <p>
     * 没有 ChatResponse 时直接返回空引用，避免 details 接口因为模型异常产生空指针
     * RetrievalAugmentationAdvisor.after 会把检索到的 Document 写入 ChatResponse metadata
     * 这里读取的是最终参与 prompt 增强的 Document，不是向量库中的全部候选结果
     *
     * @param response ChatClientResponse
     * @return 最终上下文 Document 列表
     */
    private List<Document> documents(ChatClientResponse response) {
        if (response.chatResponse() == null) {
            return contextDocuments(response);
        }
        // RetrievalAugmentationAdvisor.after 会把检索到的 Document 写入 ChatResponse metadata
        // 这里读取的是最终参与 prompt 增强的 Document，不是向量库中的全部候选结果
        Object documents = response.chatResponse().getMetadata().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
        if (!(documents instanceof List<?> list)) {
            return contextDocuments(response);
        }
        // 只保留真正的 Document，避免 metadata 中出现非预期对象时影响接口稳定性
        // RetrievalChunkReference 保留文本、分数和 metadata，供前端展示来源和排查召回质量
        return list.stream()
                .filter(Objects::nonNull)
                .filter(Document.class::isInstance)
                .map(Document.class::cast)
                .toList();
    }

    /**
     * 从 ChatClientResponse context 提取最终上下文 Document
     *
     * <p>
     * 流式场景下业务代码拿到的最后一个 chunk 不一定带完整 ChatResponse metadata
     * RetrievalAugmentationAdvisor.before 会先把检索文档写入 context，这里作为流式 references 的兜底来源
     *
     * @param response ChatClientResponse
     * @return 最终上下文 Document 列表
     */
    private List<Document> contextDocuments(ChatClientResponse response) {
        Object documents = response.context().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
        if (!(documents instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Objects::nonNull)
                .filter(Document.class::isInstance)
                .map(Document.class::cast)
                .toList();
    }

    /**
     * 文件级引用候选
     *
     * <p>
     * reference 是最终可返回给前端的轻量引用
     * bestScore 用于引用准入和排序
     * chunkCount 用于判断同一文件是否有足够 chunk 命中
     *
     * @param reference  轻量文件引用
     * @param bestScore  文件最高可信分
     * @param chunkCount 文件命中 chunk 数
     */
    private record ReferenceCandidate(
            RetrievalDocumentReference reference,
            double bestScore,
            int chunkCount
    ) {

        /**
         * 合并同一文件下的引用候选
         *
         * @param other 同一 documentId 的其他候选
         * @return 合并后的文件级引用候选
         */
        private ReferenceCandidate merge(ReferenceCandidate other) {
            // 引用展示只需要一个文件入口，因此保留首次出现的 reference
            // 分数取最高值，命中数累加，用于后续引用准入和排序
            return new ReferenceCandidate(reference, Math.max(bestScore, other.bestScore),
                    chunkCount + other.chunkCount);
        }
    }
}
