package com.metax.storage;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

/**
 * StorageDocumentService .
 *
 * <p>
 * 对象存储文档业务服务
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
public interface StorageDocumentService extends IService<StorageDocumentDO> {

    /**
     * 上传对象存储文档
     *
     * @param tenantId        租户 ID
     * @param knowledgeBaseId 知识库 ID
     * @param visibility      文档可见性
     * @param deptId          部门 ID
     * @param userId          用户 ID
     * @param documentType    文档类型
     * @param autoIndex       是否上传后自动索引
     * @param file            上传文件
     * @return 上传响应
     */
    StorageDocumentUploadResponse upload(String tenantId,
                                         String knowledgeBaseId,
                                         String visibility,
                                         String deptId,
                                         String userId,
                                         String documentType,
                                         Boolean autoIndex,
                                         MultipartFile file);

    /**
     * 分页查询对象存储文档
     *
     * @param tenantId        租户 ID
     * @param knowledgeBaseId 知识库 ID
     * @param visibility      文档可见性
     * @param deptId          部门 ID
     * @param userId          用户 ID
     * @param indexStatus     索引状态
     * @param keyword         文件名关键字
     * @param current         页码
     * @param size            每页数量
     * @return 分页结果
     */
    Page<StorageDocumentDO> pageDocuments(String tenantId,
                                          String knowledgeBaseId,
                                          String visibility,
                                          String deptId,
                                          String userId,
                                          String indexStatus,
                                          String keyword,
                                          Long current,
                                          Long size);

    /**
     * 下载对象存储文档
     *
     * @param tenantId        租户 ID
     * @param knowledgeBaseId 知识库 ID
     * @param documentId      文档 ID
     * @return 下载结果
     */
    StorageDocumentDownload download(String tenantId, String knowledgeBaseId, String documentId);

    /**
     * 提交对象存储文档索引执行
     *
     * @param tenantId        租户 ID
     * @param knowledgeBaseId 知识库 ID
     * @param documentId      文档 ID
     * @return 更新后的文档元数据
     */
    StorageDocumentDO index(String tenantId, String knowledgeBaseId, String documentId);
}
