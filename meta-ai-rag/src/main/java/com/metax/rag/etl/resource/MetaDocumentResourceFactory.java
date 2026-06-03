package com.metax.rag.etl.resource;

import com.metax.rag.config.RagProperties;
import com.metax.rag.etl.model.DocumentSourceType;
import com.metax.rag.indexing.DocumentIndexingRequest;
import com.metax.rag.storage.DocumentStorageService;
import com.metax.rag.storage.LegacyDocumentStorageService;
import com.metax.rag.storage.ObjectDocumentStorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.List;

/**
 * MetaDocumentResourceFactory .
 *
 * <p>
 * MetaAI 文档资源工厂，负责把对象存储文件流和受控本地文件统一转换为 Spring Resource
 * documentType 也在这里完成最终解析，保证 Reader、metadata 和 run 使用同一个类型值
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

    /**
     * 创建文档资源工厂
     *
     * <p>
     * storageServices 使用集合注入，避免同时存在 object / legacy 多个 DocumentStorageService Bean 时构造器注入歧义
     * 实际使用的存储实现由 metax.ai.rag.storage.provider 决定，当前默认 provider 为 object
     *
     * @param properties           RAG 配置
     * @param storageServices      所有文档存储实现
     * @param documentTypeResolver 文档类型解析器
     */
    public MetaDocumentResourceFactory(RagProperties properties,
                                       List<DocumentStorageService> storageServices,
                                       MetaDocumentTypeResolver documentTypeResolver) {
        this.properties = properties;
        this.storageService = resolveStorageService(properties, storageServices);
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

    /**
     * 按 provider 解析唯一文档存储实现
     *
     * <p>
     * 这里不直接注入 ObjectDocumentStorageService，是为了保留 provider=legacy 的扩展入口
     * 同时把未匹配或重复匹配的配置错误前置暴露，避免异步索引执行运行到读取文件时才失败
     *
     * @param properties      RAG 配置
     * @param storageServices 所有文档存储实现
     * @return 当前 provider 对应的文档存储实现
     */
    private DocumentStorageService resolveStorageService(RagProperties properties,
                                                         List<DocumentStorageService> storageServices) {
        String provider = properties.getStorage().getProvider();
        List<DocumentStorageService> matched = storageServices.stream()
                .filter(storageService -> matchesProvider(provider, storageService))
                .toList();
        if (matched.isEmpty()) {
            throw new IllegalStateException("No DocumentStorageService found for provider: " + provider);
        }
        if (matched.size() > 1) {
            throw new IllegalStateException("Multiple DocumentStorageService found for provider: " + provider);
        }
        return matched.get(0);
    }

    /**
     * 判断存储实现是否匹配当前 provider
     *
     * @param provider       配置中的存储 provider
     * @param storageService 候选存储实现
     * @return 是否匹配
     */
    private boolean matchesProvider(String provider, DocumentStorageService storageService) {
        return provider.equals(storageProvider(storageService));
    }

    /**
     * 解析存储实现对应的 provider 名称
     *
     * <p>
     * object 对应对象存储实现，legacy 对应老系统文件服务适配入口
     * 测试子类可以覆写该方法模拟不同 provider，避免依赖真实存储客户端
     *
     * @param storageService 文档存储实现
     * @return provider 名称
     */
    protected String storageProvider(DocumentStorageService storageService) {
        if (storageService instanceof ObjectDocumentStorageService) {
            return "object";
        }
        if (storageService instanceof LegacyDocumentStorageService) {
            return "legacy";
        }
        return "";
    }
}

