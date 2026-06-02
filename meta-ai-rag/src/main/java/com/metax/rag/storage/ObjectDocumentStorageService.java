package com.metax.rag.storage;

import com.metax.rag.config.RagProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.InputStream;
import java.net.URI;

/**
 * ObjectDocumentStorageService .
 *
 * <p>
 * 对象存储文档读取实现，当前默认读取 RustFS 中的知识库原始文件
 * RustFS 和 MinIO 都支持 S3 兼容协议，因此底层复用 AWS SDK S3Client
 *
 * <p>
 * RAG 入库只消费已经归档到对象存储的文件，不在索引链路中保存原始文件
 * forcePathStyle=true 适合 MinIO / RustFS 这类本地或私有化对象存储部署
 *
 * <p>
 * 配置示例
 * <pre>{@code
 * metax.ai.rag.storage.provider=object
 * metax.ai.rag.storage.endpoint=http://localhost:9000
 * metax.ai.rag.storage.bucket=meta-ai-knowledge
 * metax.ai.rag.storage.access-key=${RAG_STORAGE_ACCESS_KEY:rustfsadmin}
 * metax.ai.rag.storage.secret-key=${RAG_STORAGE_SECRET_KEY:rustfsadmin}
 * }</pre>
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/2
 */
@Service
@ConditionalOnProperty(prefix = "metax.ai.rag.storage", name = "provider", havingValue = "object", matchIfMissing = true)
public class ObjectDocumentStorageService implements DocumentStorageService {

    private final RagProperties properties;

    private final S3Client s3Client;

    public ObjectDocumentStorageService(RagProperties properties) {
        this.properties = properties;
        // S3Client 是线程安全客户端，作为服务字段复用，避免每次下载都重新创建连接资源
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(properties.getStorage().getEndpoint()))
                .region(Region.of(properties.getStorage().getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        properties.getStorage().getAccessKey(), properties.getStorage().getSecretKey())))
                .forcePathStyle(true)
                .build();
    }

    /**
     * 读取对象存储文件流
     *
     * <p>
     * 返回 InputStream 后由调用方负责关闭
     * MetaObjectStorageResource 使用 Spring Resource 读取链路关闭该流
     *
     * @param bucket    bucket 名称
     * @param objectKey object key
     * @return 对象输入流
     */
    @Override
    public InputStream getObject(String bucket, String objectKey) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();
        ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
        return response;
    }

    @Override
    public String defaultBucket() {
        return properties.getStorage().getBucket();
    }

    /**
     * 关闭 S3Client 底层连接资源
     */
    @PreDestroy
    public void close() {
        s3Client.close();
    }
}
