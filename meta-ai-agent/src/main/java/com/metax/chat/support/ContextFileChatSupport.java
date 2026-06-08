package com.metax.chat.support;

import com.metax.chat.file.MetaChatFileService;
import com.metax.rag.retrieval.advisor.MetaContextFile;
import com.metax.rag.retrieval.advisor.MetaContextFileAdvisor;
import com.metax.rag.retrieval.advisor.MetaContextFileKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * ContextFileChatSupport .
 *
 * <p>
 * 会话级文件上传、检索和 Advisor 参数注入支持
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
     * 上传并索引本轮会话文件
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param chatId   会话 ID
     * @param files    本轮上传文件
     * @return 本轮上传文件描述
     */
    public List<MetaContextFile> uploadFiles(String tenantId, String userId, String chatId, MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return List.of();
        }

        // 会话文件必须同时绑定 tenantId、userId 和 chatId，避免跨租户或跨会话复用临时文件
        ChatScope scope = chatScopeResolver.required(chatId, tenantId, userId);
        return metaChatFileService.uploadAndIndex(scope.tenantId(), scope.userId(), chatId, files);
    }

    /**
     * 上传本轮文件并解析本次对话可用文件
     *
     * <p>
     * 本轮没有上传文件时回退到当前会话历史 READY 文件，便于用户继续追问既有文件
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param chatId   会话 ID
     * @param files    本轮上传文件
     * @return 本轮上传文件和本次上下文文件
     */
    public ContextFiles uploadAndResolveContextFiles(String tenantId,
                                                     String userId,
                                                     String chatId,
                                                     MultipartFile[] files) {
        ChatScope scope = chatScopeResolver.required(chatId, tenantId, userId);

        // 先索引本轮上传文件，返回空集合表示本轮没有新文件或没有可索引文件
        List<MetaContextFile> uploaded = metaChatFileService.uploadAndIndex(scope.tenantId(), scope.userId(),
                chatId, files);

        // 本轮没有新文件时，读取当前会话已就绪文件支撑连续追问
        List<MetaContextFile> contextFiles = uploaded.isEmpty()
                ? metaChatFileService.readyFiles(scope.tenantId(), scope.userId(), chatId)
                : uploaded;
        return new ContextFiles(uploaded, contextFiles);
    }

    /**
     * 注入会话文件 Advisor 参数
     *
     * <p>
     * ORIGINAL_USER_QUERY 保留用户原始问题，避免文件检索使用已被其他 Advisor 增强后的 prompt
     * INCOMING_FILES 只表示本轮新上传文件，Advisor 会在为空时自行回退到当前会话 READY 文件
     *
     * @param spec     AdvisorSpec
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param chatId   会话 ID
     * @param msg      原始用户消息
     * @param uploaded 本轮新上传文件
     */
    public void contextFileParams(ChatClient.AdvisorSpec spec,
                                  String tenantId,
                                  String userId,
                                  String chatId,
                                  String msg,
                                  List<MetaContextFile> uploaded) {
        ChatScope scope = chatScopeResolver.resolve(chatId, tenantId, userId);

        // MessageChatMemoryAdvisor 仍然读取 conversationId，这里显式把项目 chatId 绑定给 Spring AI
        spec.param(ChatMemory.CONVERSATION_ID, chatId);

        // MetaContextFileAdvisor 使用下面这些参数检索会话级临时文件上下文
        spec.param(MetaContextFileKeys.TENANT_ID, scope.tenantId());
        spec.param(MetaContextFileKeys.USER_ID, scope.userId());
        spec.param(MetaContextFileKeys.CHAT_ID, chatId);
        spec.param(MetaContextFileKeys.ORIGINAL_USER_QUERY, msg);
        spec.param(MetaContextFileKeys.INCOMING_FILES, uploaded == null ? List.of() : uploaded);
        spec.advisors(metaContextFileAdvisor);
    }

    /**
     * 会话文件解析结果
     *
     * @param uploaded     本轮上传文件
     * @param contextFiles 本次可用上下文文件
     */
    public record ContextFiles(
            List<MetaContextFile> uploaded,
            List<MetaContextFile> contextFiles
    ) {
    }
}
