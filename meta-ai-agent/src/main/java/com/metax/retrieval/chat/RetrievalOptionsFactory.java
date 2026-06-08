package com.metax.retrieval.chat;

import com.metax.retrieval.chat.request.RetrievalChatFileRequest;
import com.metax.retrieval.chat.request.RetrievalChatRequest;
import com.metax.retrieval.debug.request.RetrievalDetailsRequest;
import com.metax.rag.retrieval.model.RetrievalOptions;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * RetrievalOptionsFactory .
 *
 * <p>
 * 统一转换知识库检索请求参数
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@Component
public class RetrievalOptionsFactory {

    /**
     * 转换知识库问答请求
     *
     * @param request 知识库问答请求参数
     * @param msg     实际用户消息
     * @return 检索参数
     */
    public RetrievalOptions create(RetrievalChatRequest request, String msg) {
        // tenantId 和 kbId 是知识库检索的硬边界，缺失时禁止进入向量库查询
        validateRetrievalScope(request.getTenantId(), request.getKbId());
        // 普通知识库问答只转换检索过滤条件，不处理文件上下文和流式响应
        return RetrievalOptions.builder()
                .tenantId(request.getTenantId())
                .kbId(request.getKbId())
                .documentId(request.getDocumentId())
                .documentType(request.getDocumentType())
                .userId(request.getUserId())
                // deptIds 从接口逗号分隔文本转换成结构化权限过滤条件
                .deptIds(parseCsv(request.getDeptIds()))
                .query(msg)
                .build();
    }

    /**
     * 转换知识库问答文件请求
     *
     * @param request 知识库问答文件请求参数
     * @param msg     实际用户消息
     * @return 检索参数
     */
    public RetrievalOptions create(RetrievalChatFileRequest request, String msg) {
        // 文件只补充 session scope 上下文，知识库过滤条件仍然必须由 tenantId 和 kbId 限定
        validateRetrievalScope(request.getTenantId(), request.getKbId());
        // 文件问答和普通问答共享 RetrievalOptions，文件列表由会话文件 Advisor 独立处理
        return RetrievalOptions.builder()
                .tenantId(request.getTenantId())
                .kbId(request.getKbId())
                .documentId(request.getDocumentId())
                .documentType(request.getDocumentType())
                .userId(request.getUserId())
                // deptIds 会进入 RetrievalFilterExpressionFactory 生成部门级权限表达式
                .deptIds(parseCsv(request.getDeptIds()))
                .query(msg)
                .build();
    }

    /**
     * 转换知识库检索调试请求
     *
     * @param request 知识库检索调试请求参数
     * @return 检索参数
     */
    public RetrievalOptions create(RetrievalDetailsRequest request) {
        // details 调试允许覆盖 topK 和 threshold，但仍然不能绕过租户和知识库边界
        validateRetrievalScope(request.getTenantId(), request.getKbId());
        // 调试接口保留 filterExpression 原样透传，便于直接验证向量库过滤表达式
        return RetrievalOptions.builder()
                .tenantId(request.getTenantId())
                .kbId(request.getKbId())
                .documentId(request.getDocumentId())
                .documentType(request.getDocumentType())
                .userId(request.getUserId())
                .deptIds(parseCsv(request.getDeptIds()))
                .topK(request.getTopK())
                .similarityThreshold(request.getThreshold())
                .filterExpression(request.getFilterExpression())
                .query(request.getMsg())
                .build();
    }

    /**
     * 校验知识库检索边界
     *
     * @param tenantId 租户 ID
     * @param kbId     知识库 ID
     */
    private void validateRetrievalScope(String tenantId, String kbId) {
        // tenantId 和 kbId 同时参与向量库过滤，任何一个为空都会扩大检索范围
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (kbId == null || kbId.isBlank()) {
            throw new IllegalArgumentException("kbId must not be blank");
        }
    }

    /**
     * 解析逗号分隔参数
     *
     * @param value 原始逗号分隔文本
     * @return 去空白后的参数列表
     */
    private List<String> parseCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        // 过滤空白项，避免请求里的连续逗号生成无意义权限条件
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(candidate -> !candidate.isBlank())
                .toList();
    }
}
