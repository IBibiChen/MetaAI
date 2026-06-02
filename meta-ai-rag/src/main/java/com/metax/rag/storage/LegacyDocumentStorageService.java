package com.metax.rag.storage;

import com.metax.rag.config.RagProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * LegacyDocumentStorageService .
 *
 * <p>
 * 老系统文件服务适配器占位实现
 * 当前只保留 RAG 文档存储端口的接入位置，真实读取逻辑需要根据老系统 HTTP、RPC 或数据库文件服务协议补齐
 *
 * <p>
 * 启用方式
 * <pre>{@code
 * metax.ai.rag.storage.provider=legacy
 * }</pre>
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/2
 */
@Service
@ConditionalOnProperty(prefix = "metax.ai.rag.storage", name = "provider", havingValue = "legacy")
public class LegacyDocumentStorageService implements DocumentStorageService {

    private final RagProperties properties;

    public LegacyDocumentStorageService(RagProperties properties) {
        this.properties = properties;
    }

    /**
     * 读取老系统文件流
     *
     * <p>
     * 第一版只保留扩展点，避免在没有明确老系统协议时伪造实现
     *
     * @param bucket    老系统文件空间或 bucket
     * @param objectKey 老系统文件 key
     * @return 对象输入流
     */
    @Override
    public InputStream getObject(String bucket, String objectKey) {
        throw new UnsupportedOperationException("Legacy document storage adapter is not implemented yet");
    }

    @Override
    public String defaultBucket() {
        return properties.getStorage().getBucket();
    }
}
