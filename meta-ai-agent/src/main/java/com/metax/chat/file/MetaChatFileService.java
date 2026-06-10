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
     * 上传聊天文件并提交异步解析任务
     *
     * <p>
     * 本方法只同步完成对象存储归档和文件元数据落库
     * OCR、chunk 切分和 session scope 向量索引由后台任务执行，前端通过 listFiles 轮询状态
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param chatId   会话 ID
     * @param files    上传文件
     * @return 已提交处理的文件状态列表
     */
    List<MetaChatFileItemResponse> uploadAndSubmitIndex(String tenantId, String userId, String chatId,
                                                        MultipartFile[] files);

    /**
     * 查询当前会话全部未删除文件
     *
     * <p>
     * 用于前端展示 UPLOADED、PARSING、READY 和 PARSE_FAILED 状态
     * 问答链路不要调用本方法，必须继续通过 readyFiles 只解析 READY 文件
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param chatId   会话 ID
     * @return 文件状态列表
     */
    List<MetaChatFileItemResponse> listFiles(String tenantId, String userId, String chatId);

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
