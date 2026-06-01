package com.metax.rag.retrieval;

import com.metax.rag.config.RagProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * RetrievalAdvisorFactory .
 *
 * <p>
 * RAG Advisor 工厂，统一构造 RetrievalAugmentationAdvisor
 * 检索默认参数来自配置文件，请求侧可通过 advisor context 覆盖 filterExpression
 *
 * <p>
 * 链路说明：本类只负责组装 Spring AI 官方 Modular RAG 组件
 * Controller 负责解析 provider、vectorStore、memory 和租户边界
 * VectorStoreRouter 负责选择正确的 VectorStore
 * RetrievalFilterExpressionFactory 负责生成结构化 metadata filter
 * 当前类负责把 QueryTransformer、VectorStoreDocumentRetriever、DocumentPostProcessor 和 ContextualQueryAugmenter 串起来
 *
 * <p>
 * Spring AI RetrievalAugmentationAdvisor 的执行顺序
 * 1、把 ChatClient 用户消息转换为 Query
 * 2、按配置执行 CompressionQueryTransformer 或 RewriteQueryTransformer
 * 3、使用 VectorStoreDocumentRetriever 到 VectorStore 召回相似 chunk
 * 4、使用 DocumentPostProcessor 去重、限制上下文数量和控制上下文长度
 * 5、使用 ContextualQueryAugmenter 把文档上下文注入用户问题
 * 6、ChatClient 把增强后的 prompt 发送给 ChatModel
 *
 * <p>
 * VectorStoreDocumentRetriever 的职责
 * 使用用户问题生成 query embedding，并到 VectorStore 做 similaritySearch
 * topK 控制最多返回多少个 chunk
 * similarityThreshold 控制最低相似度，低于阈值的结果会被过滤
 * filterExpression 控制 metadata 过滤，例如租户、知识库、文档类型
 *
 * <p>
 * QueryTransformer 的职责
 * compression 模式适合多轮追问，会把会话历史和当前追问压缩为独立检索 query
 * rewrite 模式适合单轮检索优化，会把口语化或歧义 query 改写为更适合向量检索的 query
 * query 转换使用当前 provider 对应的 ChatModel，并固定低温参数，避免多 ChatModel 环境下选错模型
 *
 * <p>
 * DocumentPostProcessor 的职责
 * 第一版先做轻量治理：去重、限制最终进入上下文的 chunk 数和限制上下文总长度
 * rerank 开关先作为扩展点保留，后续可以接 cross-encoder、DashScope rerank 或其他第三方 rerank 服务
 *
 * <p>
 * ChatClient 使用示例
 * <pre>{@code
 * chatClient.prompt()
 *     .advisors(spec -> spec.advisors(retrievalAdvisorFactory.create(vectorStore, chatModel, options, filter)))
 *     .user("Spring AI 的 ETL 是什么")
 *     .call()
 *     .content()
 * }</pre>
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
@Component
public class RetrievalAdvisorFactory {

    private final RagProperties properties;

    public RetrievalAdvisorFactory(RagProperties properties) {
        this.properties = properties;
    }

    /**
     * 构造模块化 RAG Advisor
     *
     * <p>
     * 使用配置文件中的默认 topK 和 similarityThreshold
     * 适合普通 RAG 接口和没有特殊检索参数的业务调用
     *
     * @param vectorStore 向量库
     * @return Advisor
     */
    public Advisor create(VectorStore vectorStore) {
        return create(vectorStore, properties.getRetrieval().getTopK(),
                properties.getRetrieval().getSimilarityThreshold(), null, null);
    }

    /**
     * 构造模块化 RAG Advisor
     *
     * <p>
     * 使用 RetrievalOptions 统一承载请求级检索参数
     * details 接口和普通接口都应该优先走该方法，避免 Controller 了解 Spring AI 内部扩展点
     *
     * @param vectorStore 向量库
     * @param options     检索参数
     * @return Advisor
     */
    public Advisor create(VectorStore vectorStore, RetrievalOptions options) {
        return create(vectorStore, null, options, null);
    }

    /**
     * 构造模块化 RAG Advisor
     *
     * <p>
     * chatModel 只用于 query transformer，不参与 VectorStore 检索
     * 多 ChatModel 共存时必须由调用侧按 provider 显式传入，避免 Spring 隐式选择错误模型
     *
     * @param vectorStore      向量库
     * @param chatModel        当前 provider 对应的 ChatModel
     * @param options          检索参数
     * @param filterExpression 默认过滤表达式
     * @return Advisor
     */
    public Advisor create(VectorStore vectorStore,
                          ChatModel chatModel,
                          RetrievalOptions options,
                          Filter.Expression filterExpression) {
        return create(vectorStore, options.topK(), options.similarityThreshold(), filterExpression, chatModel);
    }

    /**
     * 构造模块化 RAG Advisor
     *
     * <p>
     * 该方法适合 details 接口或调试场景
     * topK 和 similarityThreshold 允许按请求覆盖，便于比较不同召回参数的效果
     *
     * @param vectorStore         向量库
     * @param topK                检索数量
     * @param similarityThreshold 相似度阈值
     * @param filterExpression    默认过滤表达式
     * @return Advisor
     */
    public Advisor create(VectorStore vectorStore,
                          Integer topK,
                          Double similarityThreshold,
                          Filter.Expression filterExpression) {
        return create(vectorStore, topK, similarityThreshold, filterExpression, null);
    }

    /**
     * 按 Spring AI Modular RAG 组件组装 Advisor
     *
     * @param vectorStore         向量库
     * @param topK                检索数量
     * @param similarityThreshold 相似度阈值
     * @param filterExpression    默认过滤表达式
     * @param chatModel           query transformer 使用的 ChatModel
     * @return Advisor
     */
    private Advisor create(VectorStore vectorStore,
                           Integer topK,
                           Double similarityThreshold,
                           Filter.Expression filterExpression,
                           ChatModel chatModel) {
        // 请求参数为空时回落到全局默认值，保证普通 RAG 调用不需要理解底层检索参数
        int resolvedTopK = topK == null ? properties.getRetrieval().getTopK() : topK;
        double resolvedSimilarityThreshold = similarityThreshold == null
                ? properties.getRetrieval().getSimilarityThreshold() : similarityThreshold;
        // retriever 是真正访问 VectorStore 的组件，负责把 query embedding 后做 similaritySearch
        // topK 和 similarityThreshold 会直接影响召回数量、上下文质量和 token 成本
        VectorStoreDocumentRetriever.Builder retrieverBuilder = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(resolvedTopK)
                .similarityThreshold(resolvedSimilarityThreshold);
        if (filterExpression != null) {
            // 结构化过滤表达式在这里绑定到 retriever，确保检索阶段就完成租户和知识库隔离
            retrieverBuilder.filterExpression(filterExpression);
        }
        RetrievalAugmentationAdvisor.Builder advisorBuilder = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retrieverBuilder.build())
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        // 没有召回上下文时拒绝继续增强，避免 RAG 接口退化成普通聊天
                        .allowEmptyContext(false)
                        .build());
        // query transformer 位于检索前，先把用户问题整理成更适合向量检索的 query
        List<QueryTransformer> queryTransformers = queryTransformers(chatModel);
        if (!queryTransformers.isEmpty()) {
            // 只有显式启用时才接入，避免每次 RAG 都额外调用一次 ChatModel
            advisorBuilder.queryTransformers(queryTransformers);
        }
        List<DocumentPostProcessor> documentPostProcessors = documentPostProcessors(resolvedTopK,
                resolvedSimilarityThreshold, filterExpression);
        if (!documentPostProcessors.isEmpty()) {
            // post processor 位于检索后、prompt 增强前，负责治理最终进入上下文的 Document
            advisorBuilder.documentPostProcessors(documentPostProcessors);
        }
        // allowEmptyContext=false 表示没有召回上下文时不让模型自由发挥，避免 RAG 场景编造答案
        return advisorBuilder.build();
    }

    /**
     * 按配置创建检索前 query transformer
     *
     * @param chatModel 当前 provider 对应的 ChatModel
     * @return QueryTransformer 列表
     */
    private List<QueryTransformer> queryTransformers(ChatModel chatModel) {
        RagProperties.QueryTransformer config = properties.getRetrieval().getQueryTransformer();
        if (!config.isEnabled() || !StringUtils.hasText(config.getMode()) || "none".equalsIgnoreCase(config.getMode())) {
            // none 表示保留用户原始 query，适合简单单轮问题和排查召回质量
            return List.of();
        }
        if (chatModel == null) {
            // transformer 本身需要调用模型，多模型项目必须显式传入当前 provider 的 ChatModel
            throw new IllegalArgumentException("ChatModel is required when query transformer is enabled");
        }
        // query 转换追求稳定性，使用低温 ChatOptions 构造临时 ChatClient
        ChatClient.Builder builder = ChatClient.builder(chatModel)
                .defaultOptions(ChatOptions.builder()
                        .temperature(config.getTemperature())
                        .maxTokens(config.getMaxTokens())
                        .build());
        QueryTransformer transformer = switch (config.getMode().toLowerCase()) {
            // compression 适合多轮追问，会结合上下文把省略问题压缩成独立检索 query
            case "compression" -> CompressionQueryTransformer.builder()
                    .chatClientBuilder(builder)
                    .build();
            // rewrite 适合单轮问题优化，会把口语化表达改写成更适合向量召回的 query
            case "rewrite" -> RewriteQueryTransformer.builder()
                    .chatClientBuilder(builder)
                    .targetSearchSystem(config.getTargetSearchSystem())
                    .build();
            default -> throw new IllegalArgumentException("Unsupported query transformer mode: " + config.getMode());
        };
        return List.of(new TracingQueryTransformer(transformer, config.getMode()));
    }

    /**
     * 按配置创建检索后文档处理器
     *
     * @param topK                实际检索数量
     * @param similarityThreshold 实际相似度阈值
     * @param filterExpression    实际过滤表达式
     * @return DocumentPostProcessor 列表
     */
    private List<DocumentPostProcessor> documentPostProcessors(Integer topK,
                                                               Double similarityThreshold,
                                                               Filter.Expression filterExpression) {
        RagProperties.PostProcessor config = properties.getRetrieval().getPostProcessor();
        if (!config.isEnabled() && !properties.getRetrieval().getObservability().isEnabled()) {
            return List.of();
        }
        List<DocumentPostProcessor> processors = new ArrayList<>();
        // observability 开启但 post processor 关闭时，仍保留 tracing 装饰器记录召回数量和耗时
        // delegate 决定是否真的治理文档，TracingDocumentPostProcessor 只负责采集链路数据
        DocumentPostProcessor delegate = config.isEnabled()
                ? new DefaultDocumentPostProcessor(config)
                : (query, documents) -> documents;
        processors.add(new TracingDocumentPostProcessor(delegate, topK, similarityThreshold, filterExpression));
        return processors;
    }
}
