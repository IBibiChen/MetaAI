package com.metax.storage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.metax.rag.config.RagProperties;
import com.metax.rag.etl.model.DocumentSourceType;
import com.metax.rag.etl.resource.MetaDocumentTypeResolver;
import com.metax.rag.indexing.DocumentIndexingRequest;
import com.metax.rag.indexing.DocumentIndexingRun;
import com.metax.rag.indexing.DocumentIndexingService;
import com.metax.rag.model.DocumentVisibility;
import com.metax.rag.storage.ObjectStorageClient;
import com.metax.rag.storage.StoredObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * StorageDocumentServiceImpl .
 *
 * <p>
 * 基于 RustFS / S3 兼容对象存储和 MyBatis Plus 的对象存储文档服务
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
@Slf4j
@Service
public class StorageDocumentServiceImpl extends ServiceImpl<StorageDocumentMapper, StorageDocumentDO>
        implements StorageDocumentService {

    private static final long DEFAULT_CURRENT = 1L;

    private static final long DEFAULT_SIZE = 20L;

    private static final long MAX_PAGE_SIZE = 500L;

    private static final long MAX_UPLOAD_SIZE = 100 * 1024 * 1024L;

    private static final DateTimeFormatter OBJECT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM")
            .withZone(ZoneId.systemDefault());

    private final ObjectStorageClient objectStorageClient;

    private final DocumentIndexingService documentIndexingService;

    private final RagProperties ragProperties;

    private final MetaDocumentTypeResolver documentTypeResolver;

    public StorageDocumentServiceImpl(ObjectStorageClient objectStorageClient,
                                      DocumentIndexingService documentIndexingService,
                                      RagProperties ragProperties,
                                      MetaDocumentTypeResolver documentTypeResolver) {
        this.objectStorageClient = objectStorageClient;
        this.documentIndexingService = documentIndexingService;
        this.ragProperties = ragProperties;
        this.documentTypeResolver = documentTypeResolver;
    }

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
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StorageDocumentUploadResponse upload(String tenantId,
                                                String knowledgeBaseId,
                                                String visibility,
                                                String deptId,
                                                String userId,
                                                String documentType,
                                                Boolean autoIndex,
                                                MultipartFile file) {
        validateScope(tenantId, knowledgeBaseId);
        DocumentVisibility resolvedVisibility = resolveVisibility(visibility, deptId, userId);
        Assert.notNull(file, "file must not be null");
        if (file.isEmpty()) {
            throw new IllegalArgumentException("file must not be empty");
        }
        if (file.getSize() > MAX_UPLOAD_SIZE) {
            throw new IllegalArgumentException("file size must not exceed " + MAX_UPLOAD_SIZE);
        }

        String originalFilename = resolveOriginalFilename(file);
        String resolvedDocumentType = documentTypeResolver.resolve(documentType, originalFilename);
        String documentId = IdWorker.getIdStr();
        String fileSha256 = fileSha256(file);
        String objectKey = objectKey(tenantId, knowledgeBaseId, documentId, fileSha256, originalFilename);
        String bucket = objectStorageClient.defaultBucket();
        String contentType = resolveContentType(file);
        StoredObject storedObject = putObject(bucket, objectKey, file, contentType);

        StorageDocumentDO entity = new StorageDocumentDO();
        entity.setTenantId(tenantId);
        entity.setKnowledgeBaseId(knowledgeBaseId);
        entity.setVisibility(resolvedVisibility.name());
        entity.setDeptId(deptId);
        entity.setUserId(userId);
        entity.setDocumentId(documentId);
        entity.setOriginalFilename(originalFilename);
        entity.setBucket(bucket);
        entity.setObjectKey(objectKey);
        entity.setContentType(contentType);
        entity.setFileSize(file.getSize());
        entity.setFileSha256(fileSha256);
        entity.setDocumentType(resolvedDocumentType);
        entity.setSource(objectKey);
        entity.setStorageProvider(ragProperties.getStorage().getProvider());
        entity.setStorageEtag(storedObject.etag());
        entity.setStorageVersionId(storedObject.versionId());
        entity.setIndexStatus(StorageDocumentIndexStatus.UPLOADED.name());
        entity.setChunkCount(0);
        entity.setEnabled(Boolean.TRUE);
        entity.setDeleted(Boolean.FALSE);
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try {
            save(entity);
        } catch (RuntimeException ex) {
            objectStorageClient.deleteObject(bucket, objectKey);
            throw ex;
        }
        if (Boolean.TRUE.equals(autoIndex)) {
            try {
                indexSavedDocument(entity);
            } catch (RuntimeException ex) {
                entity.setIndexStatus(StorageDocumentIndexStatus.INDEX_FAILED.name());
                entity.setUpdatedAt(Instant.now());
                updateById(entity);
                log.warn("对象存储文档自动索引失败：documentId = {}，objectKey = {}",
                        entity.getDocumentId(), entity.getObjectKey(), ex);
            }
        }
        return response(entity);
    }

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
     * @return 分页对象
     */
    @Override
    public Page<StorageDocumentDO> pageDocuments(String tenantId,
                                                 String knowledgeBaseId,
                                                 String visibility,
                                                 String deptId,
                                                 String userId,
                                                 String indexStatus,
                                                 String keyword,
                                                 Long current,
                                                 Long size) {
        validateScope(tenantId, knowledgeBaseId);
        Page<StorageDocumentDO> result = page(Page.of(resolveCurrent(current), resolveSize(size)),
                query(tenantId, knowledgeBaseId, visibility, deptId, userId, indexStatus, keyword));
        result.getRecords().forEach(this::syncIndexStatus);
        return result;
    }

    /**
     * 下载对象存储文档
     *
     * @param tenantId        租户 ID
     * @param knowledgeBaseId 知识库 ID
     * @param documentId      文档 ID
     * @return 下载结果
     */
    @Override
    public StorageDocumentDownload download(String tenantId, String knowledgeBaseId, String documentId) {
        StorageDocumentDO entity = getByDocumentId(tenantId, knowledgeBaseId, documentId);
        return new StorageDocumentDownload(entity.getOriginalFilename(), entity.getContentType(), entity.getFileSize(),
                objectStorageClient.getObject(entity.getBucket(), entity.getObjectKey()));
    }

    /**
     * 提交对象存储文档索引执行
     *
     * @param tenantId        租户 ID
     * @param knowledgeBaseId 知识库 ID
     * @param documentId      文档 ID
     * @return 更新后的文档元数据
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StorageDocumentDO index(String tenantId, String knowledgeBaseId, String documentId) {
        StorageDocumentDO entity = getByDocumentId(tenantId, knowledgeBaseId, documentId);
        indexSavedDocument(entity);
        return entity;
    }

    private void indexSavedDocument(StorageDocumentDO entity) {
        entity.setIndexStatus(StorageDocumentIndexStatus.INDEXING.name());
        entity.setUpdatedAt(Instant.now());
        updateById(entity);
        try {
            documentIndexingService.submit(DocumentIndexingRequest.builder()
                    .tenantId(entity.getTenantId())
                    .knowledgeBaseId(entity.getKnowledgeBaseId())
                    .documentId(entity.getDocumentId())
                    .visibility(entity.getVisibility())
                    .deptId(entity.getDeptId())
                    .userId(entity.getUserId())
                    .documentType(entity.getDocumentType())
                    .sourceType(DocumentSourceType.OBJECT_STORAGE)
                    .source(entity.getSource())
                    .filename(entity.getOriginalFilename())
                    .bucket(entity.getBucket())
                    .objectKey(entity.getObjectKey())
                    .build(), run -> {
                entity.setLatestIndexingRunId(run.runId());
                entity.setUpdatedAt(Instant.now());
                updateById(entity);
            });
        } catch (RuntimeException ex) {
            entity.setIndexStatus(StorageDocumentIndexStatus.INDEX_FAILED.name());
            entity.setUpdatedAt(Instant.now());
            updateById(entity);
            throw ex;
        }
    }

    private void syncIndexStatus(StorageDocumentDO entity) {
        if (!StringUtils.hasText(entity.getLatestIndexingRunId())) {
            return;
        }
        DocumentIndexingRun run;
        try {
            run = documentIndexingService.getRun(entity.getLatestIndexingRunId());
        } catch (IllegalArgumentException ex) {
            return;
        }
        StorageDocumentIndexStatus indexStatus = switch (run.status()) {
            case PENDING, RUNNING -> StorageDocumentIndexStatus.INDEXING;
            case SUCCEEDED -> StorageDocumentIndexStatus.INDEXED;
            case FAILED -> StorageDocumentIndexStatus.INDEX_FAILED;
        };
        if (!indexStatus.name().equals(entity.getIndexStatus())) {
            entity.setIndexStatus(indexStatus.name());
            entity.setUpdatedAt(Instant.now());
            updateById(entity);
        }
    }

    private StorageDocumentDO getByDocumentId(String tenantId, String knowledgeBaseId, String documentId) {
        validateScope(tenantId, knowledgeBaseId);
        Assert.hasText(documentId, "documentId must not be blank");
        StorageDocumentDO entity = getOne(new LambdaQueryWrapper<StorageDocumentDO>()
                .eq(StorageDocumentDO::getTenantId, tenantId)
                .eq(StorageDocumentDO::getKnowledgeBaseId, knowledgeBaseId)
                .eq(StorageDocumentDO::getDocumentId, documentId)
                .eq(StorageDocumentDO::getDeleted, Boolean.FALSE));
        if (entity == null) {
            throw new IllegalArgumentException("storage document not found: " + documentId);
        }
        return entity;
    }

    private LambdaQueryWrapper<StorageDocumentDO> query(String tenantId,
                                                        String knowledgeBaseId,
                                                        String visibility,
                                                        String deptId,
                                                        String userId,
                                                        String indexStatus,
                                                        String keyword) {
        return new LambdaQueryWrapper<StorageDocumentDO>()
                .eq(StorageDocumentDO::getTenantId, tenantId)
                .eq(StorageDocumentDO::getKnowledgeBaseId, knowledgeBaseId)
                .eq(StorageDocumentDO::getDeleted, Boolean.FALSE)
                .eq(StringUtils.hasText(visibility), StorageDocumentDO::getVisibility, visibility)
                .eq(StringUtils.hasText(deptId), StorageDocumentDO::getDeptId, deptId)
                .eq(StringUtils.hasText(userId), StorageDocumentDO::getUserId, userId)
                .eq(StringUtils.hasText(indexStatus), StorageDocumentDO::getIndexStatus, indexStatus)
                .like(StringUtils.hasText(keyword), StorageDocumentDO::getOriginalFilename, keyword)
                .orderByDesc(StorageDocumentDO::getCreatedAt)
                .orderByDesc(StorageDocumentDO::getId);
    }

    private StoredObject putObject(String bucket, String objectKey, MultipartFile file, String contentType) {
        try (InputStream inputStream = file.getInputStream()) {
            return objectStorageClient.putObject(bucket, objectKey, inputStream, file.getSize(), contentType);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to upload object storage document", ex);
        }
    }

    private String fileSha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = file.getInputStream();
                 DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
                digestInputStream.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read upload file", ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    private String objectKey(String tenantId,
                             String knowledgeBaseId,
                             String documentId,
                             String fileSha256,
                             String originalFilename) {
        String datePath = OBJECT_DATE_FORMATTER.format(Instant.now());
        String extension = fileExtension(originalFilename);
        return "storage/%s/%s/%s/%s/%s%s".formatted(tenantId, knowledgeBaseId, datePath, documentId, fileSha256,
                extension);
    }

    private String fileExtension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index).toLowerCase();
    }

    private String resolveOriginalFilename(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename)) {
            return "upload.bin";
        }
        return filename.replace("\\", "/").substring(filename.replace("\\", "/").lastIndexOf('/') + 1);
    }

    private String resolveContentType(MultipartFile file) {
        return StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream";
    }

    private StorageDocumentUploadResponse response(StorageDocumentDO entity) {
        return new StorageDocumentUploadResponse(entity.getDocumentId(), entity.getOriginalFilename(),
                entity.getVisibility(), entity.getDeptId(), entity.getUserId(), entity.getBucket(),
                entity.getObjectKey(), entity.getFileSize(), entity.getFileSha256(), entity.getDocumentType(),
                entity.getIndexStatus(), entity.getChunkCount(), entity.getLatestIndexingRunId());
    }

    private DocumentVisibility resolveVisibility(String visibility, String deptId, String userId) {
        DocumentVisibility resolvedVisibility = DocumentVisibility.resolve(visibility);
        if (resolvedVisibility == DocumentVisibility.DEPT) {
            Assert.hasText(deptId, "deptId must not be blank when visibility is DEPT");
        }
        if (resolvedVisibility == DocumentVisibility.USER) {
            Assert.hasText(userId, "userId must not be blank when visibility is USER");
        }
        return resolvedVisibility;
    }

    private void validateScope(String tenantId, String knowledgeBaseId) {
        Assert.hasText(tenantId, "tenantId must not be blank");
        Assert.hasText(knowledgeBaseId, "knowledgeBaseId must not be blank");
    }

    private long resolveCurrent(Long current) {
        return current == null || current < DEFAULT_CURRENT ? DEFAULT_CURRENT : current;
    }

    private long resolveSize(Long size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
