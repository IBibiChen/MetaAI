package com.metax.rag.storage;

import com.metax.rag.config.RagProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

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
public class ObjectDocumentStorageService implements DocumentStorageService, ObjectStorageClient {

    private final RagProperties properties;

    private final S3Client s3Client;

    /**
     * 创建 S3 兼容对象存储文档服务
     *
     * <p>
     * 根据 metax.ai.rag.storage 配置创建 AWS SDK S3Client
     * forcePathStyle=true 适配 RustFS / MinIO 这类私有化 S3 兼容对象存储
     *
     * @param properties RAG 配置属性
     */
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
     * 上传对象存储文件
     *
     * <p>
     * inputStream 由调用方提供，当前方法只负责把流内容写入指定 bucket 和 objectKey
     * 上传成功后返回对象 etag、versionId、size 和 contentType 等元数据快照
     *
     * @param bucket      bucket 名称
     * @param objectKey   object key
     * @param inputStream 对象输入流
     * @param size        对象大小
     * @param contentType 内容类型
     * @return 上传后的对象元数据
     */
    @Override
    public StoredObject putObject(String bucket, String objectKey, InputStream inputStream, long size,
                                  String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentLength(size)
                .contentType(contentType)
                .build();
        PutObjectResponse response = s3Client.putObject(request, RequestBody.fromInputStream(inputStream, size));
        return new StoredObject(bucket, objectKey, response.eTag(), response.versionId(), size, contentType);
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

    /**
     * 读取对象存储文件元数据
     *
     * <p>
     * 该方法只执行 headObject 探测，不下载对象内容
     *
     * @param bucket    bucket 名称
     * @param objectKey object key
     * @return 对象元数据
     */
    @Override
    public StoredObject headObject(String bucket, String objectKey) {
        HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();
        HeadObjectResponse response = s3Client.headObject(request);
        return new StoredObject(bucket, objectKey, response.eTag(), response.versionId(),
                response.contentLength(), response.contentType());
    }

    /**
     * 删除对象存储文件
     *
     * @param bucket    bucket 名称
     * @param objectKey object key
     */
    @Override
    public void deleteObject(String bucket, String objectKey) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();
        s3Client.deleteObject(request);
    }

    /**
     * 默认 bucket 名称
     *
     * <p>
     * 默认值来自 metax.ai.rag.storage.bucket 配置
     *
     * @return 默认 bucket 名称
     */
    @Override
    public String defaultBucket() {
        return properties.getStorage().getBucket();
    }

    /**
     * 关闭 S3Client 底层连接资源
     *
     * <p>
     * Spring 销毁当前 Bean 时调用，避免对象存储客户端连接资源泄漏
     */
    @PreDestroy
    public void close() {
        s3Client.close();
    }
}
