package com.metax.rag.retrieval;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * MetaContextFileService .
 *
 * <p>
 * 会话级上下文文件服务接口，由应用层负责上传和持久化，RAG 模块只依赖该接口完成 Advisor 增强
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
public interface MetaContextFileService {

    /**
     * 查询当前会话可用文件
     *
     * @param tenantId       租户 ID
     * @param userId         用户 ID
     * @param conversationId 会话 ID
     * @return 会话级上下文文件
     */
    List<MetaContextFile> readyFiles(String tenantId, String userId, String conversationId);

    /**
     * 检索会话文件内容
     *
     * @param tenantId       租户 ID
     * @param userId         用户 ID
     * @param conversationId 会话 ID
     * @param files          会话级上下文文件
     * @param query          用户问题
     * @return 命中的 chunk
     */
    List<Document> retrieve(String tenantId,
                            String userId,
                            String conversationId,
                            List<MetaContextFile> files,
                            String query);
}
