package com.metax.rag.retrieval.advisor;

import com.metax.rag.model.MetadataKeys;
import cn.hutool.core.date.TimeInterval;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * MetaContextFileAdvisor .
 *
 * <p>
 * 会话级文件上下文 Advisor，负责消费 Service 层解析出的会话文件并把文件上下文追加到用户 prompt
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@Slf4j
@Component
public class MetaContextFileAdvisor implements BaseAdvisor {

    private final MetaContextFileService contextFileService;

    public MetaContextFileAdvisor(MetaContextFileService contextFileService) {
        this.contextFileService = contextFileService;
    }

    /**
     * 在模型调用前追加文件上下文
     *
     * <p>
     * 这里读取原始用户问题做文件检索，避免被其他 Advisor 增强后的 prompt 污染检索 query
     *
     * @param chatClientRequest ChatClient 请求
     * @param advisorChain      Advisor 链
     * @return 增强后的 ChatClient 请求
     */
    @Override
    @NonNull
    public ChatClientRequest before(ChatClientRequest chatClientRequest, @NonNull AdvisorChain advisorChain) {
        Map<String, Object> context = new HashMap<>(chatClientRequest.context());
        if (Boolean.TRUE.equals(context.get(MetaContextFileKeys.SKIP))) {
            return chatClientRequest;
        }
        String tenantId = contextValue(context, MetaContextFileKeys.TENANT_ID);
        String userId = contextValue(context, MetaContextFileKeys.USER_ID);
        String chatId = contextValue(context, MetaContextFileKeys.CHAT_ID);
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(userId) || !StringUtils.hasText(chatId)) {
            return chatClientRequest;
        }

        // 文件检索必须使用用户原始问题，不能使用已被其他 Advisor 增强后的 prompt
        String query = contextValue(context, MetaContextFileKeys.ORIGINAL_USER_QUERY);
        if (!StringUtils.hasText(query)) {
            query = chatClientRequest.prompt().getUserMessage().getText();
        }
        List<MetaContextFile> files = files(context, tenantId, userId, chatId);
        if (files.isEmpty()) {
            // 没有文件上下文时仍写入空 metadata，保证响应组装阶段结构稳定
            context.put(MetaContextFileKeys.CONTEXT_FILES, List.of());
            context.put(MetaContextFileKeys.DOCUMENTS, List.of());
            return chatClientRequest.mutate().context(context).build();
        }

        // 只检索 Service 层传入的 CONTEXT_FILES，避免 Advisor 隐式混入历史文件
        TimeInterval timer = new TimeInterval();
        List<Document> documents = contextFileService.retrieve(tenantId, userId, chatId, files, query);
        log.info("会话文件上下文检索完成：tenantId = {}，userId = {}，chatId = {}，fileCount = {}，hitCount = {}，durationMs = {}",
                tenantId, userId, chatId, files.size(), documents.size(), timer.intervalMs());
        context.put(MetaContextFileKeys.CONTEXT_FILES, files);
        context.put(MetaContextFileKeys.DOCUMENTS, documents);
        return chatClientRequest.mutate()
                .prompt(chatClientRequest.prompt().augmentUserMessage(augmentPrompt(
                        chatClientRequest.prompt().getUserMessage().getText(), files, documents)))
                .context(context)
                .build();
    }

    /**
     * 在模型调用后把文件上下文元数据写回响应
     *
     * @param chatClientResponse ChatClient 响应
     * @param advisorChain       Advisor 链
     * @return 带文件上下文 metadata 的 ChatClient 响应
     */
    @Override
    @NonNull
    public ChatClientResponse after(ChatClientResponse chatClientResponse, @NonNull AdvisorChain advisorChain) {
        ChatResponse.Builder chatResponseBuilder = chatClientResponse.chatResponse() == null
                ? ChatResponse.builder()
                : ChatResponse.builder().from(chatClientResponse.chatResponse());
        // 把请求阶段写入的文件上下文透传到 ChatResponse metadata，供 done 事件和非流式响应组装
        chatResponseBuilder.metadata(MetaContextFileKeys.CONTEXT_FILES,
                chatClientResponse.context().getOrDefault(MetaContextFileKeys.CONTEXT_FILES, List.of()));
        chatResponseBuilder.metadata(MetaContextFileKeys.DOCUMENTS,
                chatClientResponse.context().getOrDefault(MetaContextFileKeys.DOCUMENTS, List.of()));
        return ChatClientResponse.builder()
                .chatResponse(chatResponseBuilder.build())
                .context(chatClientResponse.context())
                .build();
    }

    @Override
    public int getOrder() {
        // 文件上下文应在普通记忆之后、日志之前执行，尽量靠后追加临时文件内容
        return Ordered.LOWEST_PRECEDENCE - 20;
    }

    /**
     * 解析本次参与上下文增强的会话文件
     *
     * <p>
     * CONTEXT_FILES 表示本次参与上下文增强的所有文件，是问答链路的标准输入
     * Advisor 不再自行回退查询 READY 文件，避免文件选择逻辑分散在多层
     *
     * @param context  Advisor context
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param chatId   会话 ID
     * @return 本次参与上下文增强的会话文件
     */
    @SuppressWarnings("unchecked")
    private List<MetaContextFile> files(Map<String, Object> context,
                                        String tenantId,
                                        String userId,
                                        String chatId) {
        Object contextFiles = context.get(MetaContextFileKeys.CONTEXT_FILES);
        if (contextFiles instanceof List<?> list && list.stream().allMatch(MetaContextFile.class::isInstance)) {
            return (List<MetaContextFile>) list;
        }
        // context 中没有合法 CONTEXT_FILES 时直接视为无文件，不再自行回退查 READY 文件
        return List.of();
    }

    /**
     * 构造带会话文件上下文的用户 prompt
     *
     * <p>
     * 这里明确告诉模型上传文件不是知识库引用来源，避免普通 RAG references 和会话文件上下文混在一起
     *
     * @param userText  原始用户文本
     * @param files     本次参与上下文增强的会话文件
     * @param documents 命中的会话文件 chunk
     * @return 增强后的用户 prompt
     */
    private String augmentPrompt(String userText, List<MetaContextFile> files, List<Document> documents) {
        String fileList = files.stream()
                .map(file -> "- %s (%s，fileId = %s)".formatted(file.fileName(), file.documentType(), file.fileId()))
                .collect(Collectors.joining(System.lineSeparator()));
        String context = documents == null || documents.isEmpty()
                ? "未检索到与问题相关的上传文件内容"
                : documents.stream()
                .filter(Objects::nonNull)
                .map(document -> {
                    Object fileName = document.getMetadata().get(MetadataKeys.FILE_NAME);
                    Object chunkIndex = document.getMetadata().get(MetadataKeys.CHUNK_INDEX);
                    return """
                            [上传文件：%s，片段：%s]
                            %s
                            """.formatted(fileName, chunkIndex, document.getText());
                })
                .collect(Collectors.joining(System.lineSeparator()));
        return """
                %s
                
                当前会话上传文件上下文：
                请把以下内容视为用户当前会话上传的临时文件上下文，不要把它当作知识库引用来源。
                如果上传文件中没有足够依据，请明确说明上传文件未提供相关信息。
                
                当前会话文件：
                %s
                
                检索到的上传文件内容：
                %s
                """.formatted(userText, fileList, context);
    }

    /**
     * 从 Advisor context 中读取字符串值
     *
     * @param context Advisor context
     * @param key     key
     * @return 字符串值
     */
    private String contextValue(Map<String, Object> context, String key) {
        Object value = context.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
