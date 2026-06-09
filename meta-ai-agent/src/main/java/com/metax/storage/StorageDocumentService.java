package com.metax.storage;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.metax.storage.request.StorageDocumentPageRequest;
import com.metax.storage.request.StorageDocumentUploadRequest;
import com.metax.storage.response.StorageDocumentDownloadResponse;
import com.metax.storage.response.StorageDocumentUploadResponse;

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
     * @param request 上传请求
     * @return 上传响应
     */
    StorageDocumentUploadResponse upload(StorageDocumentUploadRequest request);

    /**
     * 分页查询对象存储文档
     *
     * @param request 分页查询请求
     * @return 分页结果
     */
    Page<StorageDocumentDO> pageDocuments(StorageDocumentPageRequest request);

    /**
     * 下载对象存储文档
     *
     * @param tenantId   租户 ID
     * @param kbId       知识库 ID
     * @param documentId 文档 ID
     * @return 下载结果
     */
    StorageDocumentDownloadResponse download(String tenantId, String kbId, String documentId);

    /**
     * 按全局 documentId 下载对象存储文档
     *
     * <p>
     * 供回答来源文档点击下载使用，调用方只需要传入普通响应 references 中的 documentId
     *
     * @param documentId 文档 ID
     * @return 下载结果
     */
    StorageDocumentDownloadResponse download(String documentId);

    /**
     * 提交对象存储文档索引执行
     *
     * @param tenantId   租户 ID
     * @param kbId       知识库 ID
     * @param documentId 文档 ID
     * @return 更新后的文档元数据
     */
    StorageDocumentDO index(String tenantId, String kbId, String documentId);
}
