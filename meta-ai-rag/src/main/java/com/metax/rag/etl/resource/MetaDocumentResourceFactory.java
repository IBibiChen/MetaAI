package com.metax.rag.etl.resource;

import com.metax.rag.indexing.DocumentIndexingRequest;
import com.metax.rag.storage.DocumentStorageService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * MetaDocumentResourceFactory .
 *
 * <p>
 * MetaAI 文档资源工厂，负责把对象存储文件流转换为 Spring Resource
 * documentType 也在这里完成最终解析，保证 Reader、metadata 和 run 使用同一个类型值
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
@Component
public class MetaDocumentResourceFactory {

    private final DocumentStorageService storageService;

    private final MetaDocumentTypeResolver documentTypeResolver;

    /**
     * 创建文档资源工厂
     *
     * <p>
     * 当前知识库文档只从 storage 模块归档后的对象存储读取
     * 文件上传、默认 bucket 和对象写入职责属于 ObjectStorageClient，不放在该读取工厂中
     *
     * @param storageService       文档存储读取端口
     * @param documentTypeResolver 文档类型解析器
     */
    public MetaDocumentResourceFactory(DocumentStorageService storageService,
                                       MetaDocumentTypeResolver documentTypeResolver) {
        this.storageService = storageService;
        this.documentTypeResolver = documentTypeResolver;
    }

    /**
     * 创建文档资源
     *
     * @param request RAG 文档索引请求
     * @return 文档资源
     */
    public MetaDocumentResource create(DocumentIndexingRequest request) {
        return objectStorage(request);
    }

    private MetaDocumentResource objectStorage(DocumentIndexingRequest request) {
        // bucket 和 objectKey 是对象存储索引的真实数据来源，source 只用于展示和引用
        if (!StringUtils.hasText(request.bucket())) {
            throw new IllegalArgumentException("bucket must not be blank for object storage document");
        }
        if (!StringUtils.hasText(request.objectKey())) {
            throw new IllegalArgumentException("objectKey must not be blank for object storage document");
        }
        String sourceName = request.objectKey();
        String documentType = documentTypeResolver.resolve(request.documentType(), sourceName);
        // MetaObjectStorageResource 会在 Reader 读取时按需打开对象流，避免提前把大文件读入内存
        Resource resource = new MetaObjectStorageResource(storageService, request.bucket(), request.objectKey());
        return new MetaDocumentResource(resource, documentType, resolveSource(request.source(), sourceName));
    }

    private String resolveSource(String source, String fallback) {
        // source 面向引用展示，未显式传入时使用 objectKey 兜底
        return StringUtils.hasText(source) ? source : fallback;
    }
}

