package com.metax.chat.file;

import com.metax.rag.retrieval.advisor.MetaContextFile;
import com.metax.rag.retrieval.advisor.MetaContextFileService;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * MetaChatFileService .
 *
 * <p>
 * 聊天文件服务，负责会话级文件上传、解析、临时索引和检索
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
public interface MetaChatFileService extends MetaContextFileService {

    /**
     * 上传并解析聊天文件
     *
     * <p>
     * 同步完成对象存储归档、文本解析、chunk 切分和 session scope 向量索引
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param chatId   会话 ID
     * @param files    上传文件
     * @return 已解析文件列表
     */
    List<MetaContextFile> uploadAndIndex(String tenantId, String userId, String chatId, MultipartFile[] files);

    /**
     * 查询当前会话可用文件
     *
     * <p>
     * 用于 fileIds 为空时的多轮追问回退策略
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param chatId   会话 ID
     * @return 文件列表
     */
    List<MetaContextFile> readyFiles(String tenantId, String userId, String chatId);

    /**
     * 按文件 ID 查询当前会话可用文件
     *
     * <p>
     * 用于用户显式指定本轮参与上下文增强的文件集合
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param chatId   会话 ID
     * @param fileIds  文件 ID 列表
     * @return 文件列表
     */
    List<MetaContextFile> readyFiles(String tenantId, String userId, String chatId, List<String> fileIds);

}
