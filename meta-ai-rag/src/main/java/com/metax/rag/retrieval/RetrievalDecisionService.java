package com.metax.rag.retrieval;

import com.metax.rag.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * RetrievalDecisionService .
 *
 * <p>
 * 普通 RAG 对话检索门控服务，决定当前轮是否需要执行知识库检索
 *
 * <p>
 * 规则能确定时直接返回，避免每次请求都额外调用 ChatModel
 * 规则无法判断且模式为 hybrid 时，再调用 ChatModel 做轻量意图分类
 * details / search 这类排查接口不应该经过该服务，必须保留强制检索能力
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/4
 */
@Service
public class RetrievalDecisionService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalDecisionService.class);

    private static final String MODE_RULE = "rule";

    private static final String MODE_HYBRID = "hybrid";

    private static final String MODE_LLM = "llm";

    private static final String CLASSIFIER_SYSTEM = """
            你是检索路由分类器，只判断用户当前问题是否需要查询知识库
            
            返回规则：
            - 只返回 RETRIEVE 或 SKIP
            - 如果问题需要基于知识库、文档、资料、文件、上传内容回答，返回 RETRIEVE
            - 如果问题是问候、感谢、助手身份、闲聊、通用能力说明，返回 SKIP
            - 如果问题是“继续说”“它有什么风险”“这个怎么处理”等依赖上一轮知识库内容的追问，返回 RETRIEVE
            - 不要解释原因
            """;

    private static final List<String> RETRIEVE_KEYWORDS = List.of(
            "知识库", "文档", "资料", "文件", "根据", "基于", "总结", "检索", "查找", "引用", "来源", "上传的",
            "上传内容", "原文", "材料", "报告", "合同", "方案", "手册"
    );

    private static final List<String> SKIP_RETRIEVAL_PATTERNS = List.of(
            "不使用知识库", "不要使用知识库", "不用知识库", "不要查知识库", "不查知识库",
            "不要检索", "不用检索", "不要查询知识库", "不查询知识库", "基于你自己的知识",
            "基于你自己学习的", "基于你的训练数据", "基于模型自身知识", "直接回答"
    );

    private static final List<String> SKIP_PATTERNS = List.of(
            "你是谁", "你是什么", "你叫什么", "你好", "您好", "谢谢", "感谢", "thanks", "thank you",
            "hi", "hello", "你能做什么", "你可以做什么", "介绍一下你自己", "帮我做什么"
    );

    private final RagProperties properties;

    private final ChatModel chatModel;

    public RetrievalDecisionService(RagProperties properties, ChatModel chatModel) {
        this.properties = properties;
        this.chatModel = chatModel;
    }

    /**
     * 决定当前轮普通 RAG 对话是否执行检索
     *
     * @param options 检索参数
     * @return 检索决策结果
     */
    public RetrievalDecisionResult decide(RetrievalOptions options) {
        Objects.requireNonNull(options, "RetrievalOptions must not be null");
        String mode = normalizedMode();
        if (MODE_LLM.equals(mode)) {
            return decideByLlm(options.getQuery());
        }

        RuleDecision ruleDecision = decideByRule(options);
        if (ruleDecision.decision() != null) {
            return new RetrievalDecisionResult(ruleDecision.decision(), ruleDecision.reason());
        }
        if (MODE_HYBRID.equals(mode)) {
            return decideByLlm(options.getQuery());
        }
        return RetrievalDecisionResult.retrieve("rule_unknown_default_retrieve");
    }

    /**
     * 使用确定性规则判断是否检索
     *
     * <p>
     * 显式文档范围和知识库关键词优先判定为检索，助手身份、问候和感谢等闲聊优先判定为跳过
     * 规则无法覆盖的上下文追问返回 rule_unknown，由 hybrid 模式继续交给 ChatModel 判断
     *
     * @param options 检索参数
     * @return 规则决策结果
     */
    private RuleDecision decideByRule(RetrievalOptions options) {
        if (StringUtils.hasText(options.getDocumentId())) {
            return new RuleDecision(RetrievalDecision.RETRIEVE, "document_id_present");
        }
        if (StringUtils.hasText(options.getDocumentType())) {
            return new RuleDecision(RetrievalDecision.RETRIEVE, "document_type_present");
        }
        String query = normalizedQuery(options.getQuery());
        if (!StringUtils.hasText(query)) {
            return new RuleDecision(RetrievalDecision.SKIP, "blank_query");
        }
        if (containsAny(query, SKIP_RETRIEVAL_PATTERNS)) {
            return new RuleDecision(RetrievalDecision.SKIP, "skip_retrieval_pattern");
        }
        if (containsAny(query, RETRIEVE_KEYWORDS)) {
            return new RuleDecision(RetrievalDecision.RETRIEVE, "retrieve_keyword");
        }
        if (containsAny(query, SKIP_PATTERNS)) {
            return new RuleDecision(RetrievalDecision.SKIP, "skip_pattern");
        }
        return new RuleDecision(null, "rule_unknown");
    }

    /**
     * 使用 ChatModel 判断是否检索
     *
     * <p>
     * 该方法只在 llm 模式或 hybrid 规则未知时调用
     * 模型只允许输出 RETRIEVE 或 SKIP，非法输出、空响应或调用异常都默认执行检索，避免误跳过知识库问题
     *
     * @param query 用户问题
     * @return 检索决策结果
     */
    private RetrievalDecisionResult decideByLlm(String query) {
        try {
            ChatOptions options = ChatOptions.builder()
                    .temperature(properties.getRetrieval().getDecision().getTemperature())
                    .maxTokens(properties.getRetrieval().getDecision().getMaxTokens())
                    .build();
            Prompt prompt = new Prompt(List.of(new SystemMessage(CLASSIFIER_SYSTEM),
                    new UserMessage("用户问题：\n" + nullToEmpty(query))), options);
            String answer = chatModel.call(prompt).getResult().getOutput().getText();
            String normalizedAnswer = normalizedQuery(answer);
            if (normalizedAnswer.startsWith(RetrievalDecision.SKIP.name().toLowerCase(Locale.ROOT))) {
                return RetrievalDecisionResult.skip("llm_skip");
            }
            if (normalizedAnswer.startsWith(RetrievalDecision.RETRIEVE.name().toLowerCase(Locale.ROOT))) {
                return RetrievalDecisionResult.retrieve("llm_retrieve");
            }
            log.warn("检索决策模型返回非法结果：answer = {}", answer);
            return RetrievalDecisionResult.retrieve("llm_invalid_default_retrieve");
        } catch (Exception e) {
            log.warn("检索决策模型调用失败，默认执行检索：message = {}", e.getMessage());
            return RetrievalDecisionResult.retrieve("llm_error_default_retrieve");
        }
    }

    /**
     * 解析检索决策模式
     *
     * <p>
     * 配置缺失或非法时回退 hybrid，避免因为配置拼写错误导致检索门控完全失效
     *
     * @return 检索决策模式
     */
    private String normalizedMode() {
        String mode = properties.getRetrieval().getDecision().getMode();
        if (!StringUtils.hasText(mode)) {
            return MODE_HYBRID;
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        if (MODE_RULE.equals(normalized) || MODE_HYBRID.equals(normalized) || MODE_LLM.equals(normalized)) {
            return normalized;
        }
        log.warn("未知检索决策模式，回退 hybrid：mode = {}", mode);
        return MODE_HYBRID;
    }

    /**
     * 归一化用户问题或模型输出
     *
     * @param value 原始字符串
     * @return 小写并去除首尾空白后的字符串
     */
    private String normalizedQuery(String value) {
        return nullToEmpty(value).trim().toLowerCase(Locale.ROOT);
    }

    /**
     * null 转空字符串
     *
     * @param value 原始字符串
     * @return 非 null 字符串
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 判断 query 是否包含任一候选短语
     *
     * @param query      归一化后的用户问题
     * @param candidates 候选短语
     * @return 是否命中
     */
    private boolean containsAny(String query, List<String> candidates) {
        return candidates.stream()
                .map(candidate -> candidate.toLowerCase(Locale.ROOT))
                .anyMatch(query::contains);
    }

    private record RuleDecision(
            RetrievalDecision decision,
            String reason
    ) {
    }
}
