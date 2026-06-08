package com.metax.chat.file;

import com.metax.rag.retrieval.MetaContextFile;
import com.metax.rag.retrieval.MetaContextFileService;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * ChatFileService .
 *
 * <p>
 * 聊天文件服务，负责会话级文件上传、解析、临时索引和检索
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
public interface ChatFileService extends MetaContextFileService {

    /**
     * 上传并解析聊天文件
     *
     * @param tenantId       租户 ID
     * @param userId         用户 ID
     * @param conversationId 会话 ID
     * @param files          上传文件
     * @return 已解析文件列表
     */
    List<MetaContextFile> uploadAndIndex(String tenantId, String userId, String conversationId, MultipartFile[] files);

    /**
     * 查询当前会话可用文件
     *
     * @param tenantId       租户 ID
     * @param userId         用户 ID
     * @param conversationId 会话 ID
     * @return 文件列表
     */
    List<MetaContextFile> readyFiles(String tenantId, String userId, String conversationId);
}
