package com.metax.rag.storage;

import com.metax.rag.config.RagProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

/**
 * RustFsStorageService .
 *
 * <p>
 * RustFS 对象存储访问服务，按 S3 兼容协议保存知识库原始文件
 * RAG 入库只读取 RustFS 中的对象，不直接依赖本地临时路径
 *
 * <p>
 * 设计说明：为什么原始文件要放对象存储
 * VectorStore 只保存 chunk 文本、向量和 metadata，不适合保存用户上传的原始文件
 * RustFS 保存原始文件，VectorStore 保存检索数据，两者通过 source / objectKey 建立关联
 * 后续如果需要重新切分、重建索引或审计来源，可以从 RustFS 重新读取原始文件
 *
 * <p>
 * RustFS 使用 S3 兼容协议，因此这里直接使用 AWS SDK S3Client
 * forcePathStyle=true 适合 MinIO / RustFS 这类本地或私有化对象存储部署
 *
 * <p>
 * 配置示例
 * <pre>{@code
 * metax.ai.rag.storage.endpoint=http://localhost:9000
 * metax.ai.rag.storage.bucket=meta-ai-knowledge
 * metax.ai.rag.storage.access-key=${RUSTFS_ACCESS_KEY:rustfsadmin}
 * metax.ai.rag.storage.secret-key=${RUSTFS_SECRET_KEY:rustfsadmin}
 * }</pre>
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
@Service
public class RustFsStorageService {

    private final RagProperties properties;

    private final S3Client s3Client;

    public RustFsStorageService(RagProperties properties) {
        this.properties = properties;
        // S3Client 是线程安全客户端，作为服务字段复用，避免每次上传或下载都重新创建连接资源
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(properties.getStorage().getEndpoint()))
                .region(Region.of(properties.getStorage().getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        properties.getStorage().getAccessKey(), properties.getStorage().getSecretKey())))
                .forcePathStyle(true)
                .build();
    }

    /**
     * 上传知识库文件到 RustFS
     *
     * <p>
     * objectKey 使用 tenantId / knowledgeBaseId / 日期 / UUID / 原文件名 分层
     * 这样可以按租户和知识库快速定位对象，也能避免同名文件互相覆盖
     *
     * @param file            上传文件
     * @param tenantId        租户 ID
     * @param knowledgeBaseId 知识库 ID
     * @return RustFS object key
     */
    public String upload(MultipartFile file, String tenantId, String knowledgeBaseId) {
        String objectKey = createObjectKey(file.getOriginalFilename(), tenantId, knowledgeBaseId);
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.getStorage().getBucket())
                    .key(objectKey)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            return objectKey;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to upload knowledge file to RustFS: " + objectKey, ex);
        }
    }

    /**
     * 读取 RustFS 对象流
     *
     * <p>
     * 返回 InputStream 后由调用方负责关闭
     * MetaObjectStorageResource 使用 try-with-resources 关闭该流
     *
     * @param bucket    bucket 名称
     * @param objectKey object key
     * @return 对象输入流
     */
    public InputStream getObject(String bucket, String objectKey) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();
        ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
        return response;
    }

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

    private String createObjectKey(String originalFilename, String tenantId, String knowledgeBaseId) {
        // UUID 只用于对象存储路径去重，不参与 RAG documentId，业务幂等仍由 documentId 控制
        String filename = originalFilename == null || originalFilename.isBlank() ? "document" : originalFilename;
        LocalDate now = LocalDate.now();
        return "knowledge/%s/%s/%s/%s/%s".formatted(tenantId, knowledgeBaseId, now.getYear(),
                "%02d".formatted(now.getMonthValue()), UUID.randomUUID() + "-" + filename);
    }
}
