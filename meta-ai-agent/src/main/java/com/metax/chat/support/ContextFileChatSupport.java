package com.metax.chat.support;

import com.metax.chat.file.MetaChatFileService;
import com.metax.rag.retrieval.advisor.MetaContextFile;
import com.metax.rag.retrieval.advisor.MetaContextFileAdvisor;
import com.metax.rag.retrieval.advisor.MetaContextFileKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ContextFileChatSupport .
 *
 * <p>
 * 会话级文件上传、fileIds 解析和 Advisor 参数注入支持
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@Component
@RequiredArgsConstructor
public class ContextFileChatSupport {

    private final MetaChatFileService metaChatFileService;

    private final MetaContextFileAdvisor metaContextFileAdvisor;

    private final ChatScopeResolver chatScopeResolver;

    /**
     * 按文件 ID 解析本次可用 READY 会话文件
     *
     * <p>
     * fileIds 为空时回退当前会话 READY 文件，非空时只使用显式指定文件
     * 这是问答链路唯一的 fileIds 解析入口，Advisor 不再自行查询 READY 文件
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param chatId   会话 ID
     * @param fileIds  会话文件 ID 列表
     * @return 本次可用上下文文件
     */
    public List<MetaContextFile> resolveReadyFiles(String tenantId,
                                                   String userId,
                                                   String chatId,
                                                   List<String> fileIds) {
        // 先解析并校验会话归属，防止跨租户、跨用户或跨会话引用文件
        ChatScope scope = chatScopeResolver.required(chatId, tenantId, userId);
        List<String> resolvedFileIds = normalizeFileIds(fileIds);
        List<MetaContextFile> files;
        if (resolvedFileIds.isEmpty()) {
            // 空 fileIds 表示多轮追问沿用当前会话已解析完成的 READY 文件
            files = metaChatFileService.readyFiles(scope.tenantId(), scope.userId(), chatId);
        } else {
            // 显式 fileIds 表示用户指定本轮文件集合，不混入历史 READY 文件
            files = metaChatFileService.readyFiles(scope.tenantId(), scope.userId(), chatId, resolvedFileIds);
        }
        return files == null ? List.of() : files;
    }

    /**
     * 归一化文件 ID 列表
     *
     * <p>
     * GET query 可能把多个 fileId 放在一个逗号分隔字符串中，这里统一拆成去重后的列表
     *
     * @param fileIds 原始文件 ID 列表
     * @return 去空白、去重后的文件 ID 列表
     */
    private List<String> normalizeFileIds(List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        return fileIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .flatMap(id -> java.util.Arrays.stream(id.split(",")))
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .distinct()
                .toList();
    }

    /**
     * 注入会话文件 Advisor 参数
     *
     * <p>
     * ORIGINAL_USER_QUERY 保留用户原始问题，避免文件检索使用已被其他 Advisor 增强后的 prompt
     * CONTEXT_FILES 表示本次参与上下文增强的所有文件，由 Service 层统一解析
     * Advisor 只消费这些上下文参数，不理解 HTTP 请求体和 fileIds
     *
     * @param spec         AdvisorSpec
     * @param tenantId     租户 ID
     * @param userId       用户 ID
     * @param chatId       会话 ID
     * @param msg          原始用户消息
     * @param contextFiles 本次参与上下文增强的会话文件
     */
    public void contextFileParams(ChatClient.AdvisorSpec spec,
                                  String tenantId,
                                  String userId,
                                  String chatId,
                                  String msg,
                                  List<MetaContextFile> contextFiles) {
        ChatScope scope = chatScopeResolver.resolve(chatId, tenantId, userId);
        List<MetaContextFile> resolvedContextFiles = contextFiles == null ? List.of() : contextFiles;

        // MessageChatMemoryAdvisor 仍然读取 conversationId，这里显式把项目 chatId 绑定给 Spring AI
        spec.param(ChatMemory.CONVERSATION_ID, chatId);

        // 文件 Advisor 用租户、用户、会话和原始问题检索 session scope 文件 chunk
        spec.param(MetaContextFileKeys.TENANT_ID, scope.tenantId());
        spec.param(MetaContextFileKeys.USER_ID, scope.userId());
        spec.param(MetaContextFileKeys.CHAT_ID, chatId);
        spec.param(MetaContextFileKeys.ORIGINAL_USER_QUERY, msg);
        spec.param(MetaContextFileKeys.CONTEXT_FILES, resolvedContextFiles);
        spec.advisors(metaContextFileAdvisor);
    }

}
