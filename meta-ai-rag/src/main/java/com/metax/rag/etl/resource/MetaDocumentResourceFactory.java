package com.metax.rag.etl.resource;

import com.metax.rag.config.RagProperties;
import com.metax.rag.etl.model.DocumentSourceType;
import com.metax.rag.indexing.DocumentIndexingRequest;
import com.metax.rag.storage.DocumentStorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;

/**
 * MetaDocumentResourceFactory .
 *
 * <p>
 * MetaAI 文档资源工厂，负责把对象存储文件流和受控本地文件统一转换为 Spring Resource
 * documentType 也在这里完成最终解析，保证 Reader、metadata 和 job 使用同一个类型值
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
@Component
public class MetaDocumentResourceFactory {

    private final RagProperties properties;

    private final DocumentStorageService storageService;

    private final MetaDocumentTypeResolver documentTypeResolver;

    public MetaDocumentResourceFactory(RagProperties properties,
                                       DocumentStorageService storageService,
                                       MetaDocumentTypeResolver documentTypeResolver) {
        this.properties = properties;
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
        if (request.sourceType() == DocumentSourceType.LOCAL_FILE) {
            return localFile(request);
        }
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

    private MetaDocumentResource localFile(DocumentIndexingRequest request) {
        // 本地文件只作为受控调试入口，必须限制在 metax.ai.rag.storage.local-root 内
        if (!StringUtils.hasText(request.localPath())) {
            throw new IllegalArgumentException("localPath must not be blank for local file document");
        }
        Path localRoot = Path.of(properties.getStorage().getLocalRoot()).toAbsolutePath().normalize();
        Path relativePath = Path.of(request.localPath()).normalize();
        if (relativePath.isAbsolute()) {
            throw new IllegalArgumentException("localPath must be relative to metax.ai.rag.storage.local-root");
        }
        Path resolvedPath = localRoot.resolve(relativePath).normalize();
        if (!resolvedPath.startsWith(localRoot)) {
            throw new IllegalArgumentException("localPath must stay inside metax.ai.rag.storage.local-root");
        }
        if (!resolvedPath.toFile().isFile()) {
            throw new IllegalArgumentException("localPath file does not exist: " + request.localPath());
        }
        String sourceName = request.localPath();
        String documentType = documentTypeResolver.resolve(request.documentType(), sourceName);
        return new MetaDocumentResource(new FileSystemResource(resolvedPath), documentType,
                resolveSource(request.source(), sourceName));
    }

    private String resolveSource(String source, String fallback) {
        // source 面向引用展示，未显式传入时使用 objectKey 或 localPath 兜底
        return StringUtils.hasText(source) ? source : fallback;
    }
}

