package com.metax.storage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.metax.rag.config.RagProperties;
import com.metax.rag.etl.resource.MetaDocumentTypeResolver;
import com.metax.rag.indexing.DocumentIndexingRequest;
import com.metax.rag.indexing.DocumentIndexingRun;
import com.metax.rag.indexing.DocumentIndexingService;
import com.metax.rag.model.DocumentVisibility;
import com.metax.rag.storage.ObjectStorageClient;
import com.metax.rag.storage.StoredObject;
import com.metax.storage.request.StorageDocumentPageRequest;
import com.metax.storage.request.StorageDocumentUploadRequest;
import com.metax.storage.response.StorageDocumentDownloadResponse;
import com.metax.storage.response.StorageDocumentUploadResponse;
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

    /**
     * 默认页码
     */
    private static final long DEFAULT_CURRENT = 1L;

    /**
     * 默认每页数量
     */
    private static final long DEFAULT_SIZE = 20L;

    /**
     * 最大分页数量
     */
    private static final long MAX_PAGE_SIZE = 500L;

    /**
     * 单个对象存储文档最大上传大小
     */
    private static final long MAX_UPLOAD_SIZE = 100 * 1024 * 1024L;

    /**
     * 对象存储路径按月份分桶，避免单目录对象过多
     */
    private static final DateTimeFormatter OBJECT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM")
            .withZone(ZoneId.systemDefault());

    /**
     * 对象存储客户端
     *
     * <p>
     * 当前实现通过 RustFS / S3 兼容接口保存和读取原始文件
     */
    private final ObjectStorageClient objectStorageClient;

    /**
     * 文档索引服务
     *
     * <p>
     * 上传后自动索引和手动索引都会提交 DocumentIndexingRequest 到该服务
     */
    private final DocumentIndexingService documentIndexingService;

    /**
     * RAG 配置属性
     *
     * <p>
     * 当前用于读取对象存储 provider，写入 StorageDocumentDO 便于排查来源
     */
    private final RagProperties ragProperties;

    /**
     * 文档类型解析器
     *
     * <p>
     * 当请求未传 documentType 时，根据上传文件名推断 Reader 类型
     */
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
     * <p>
     * 上传链路同步完成对象存储写入和元数据表保存，autoIndex 为 true 时继续提交异步索引执行
     *
     * @param request 上传请求
     * @return 上传响应
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StorageDocumentUploadResponse upload(StorageDocumentUploadRequest request) {
        String tenantId = request.getTenantId();
        String kbId = request.getKbId();
        MultipartFile file = request.getFile();

        // 租户和知识库是对象存储文档的硬边界，后续索引和检索都会依赖这两个字段
        validateScope(tenantId, kbId);
        DocumentVisibility resolvedVisibility = resolveVisibility(request.getVisibility(), request.getDeptId(),
                request.getUserId());
        Assert.notNull(file, "file must not be null");
        if (file.isEmpty()) {
            throw new IllegalArgumentException("file must not be empty");
        }
        if (file.getSize() > MAX_UPLOAD_SIZE) {
            throw new IllegalArgumentException("file size must not exceed " + MAX_UPLOAD_SIZE);
        }

        String originalFilename = resolveOriginalFilename(file);
        String resolvedDocumentType = documentTypeResolver.resolve(request.getDocumentType(), originalFilename);
        String documentId = newDocumentId();
        String fileSha256 = fileSha256(file);
        String objectKey = objectKey(tenantId, kbId, documentId, fileSha256, originalFilename);
        String bucket = objectStorageClient.defaultBucket();
        String contentType = resolveContentType(file);
        // 原始文件先进入对象存储，后续下载和索引 Reader 都从对象存储读取
        StoredObject storedObject = putObject(bucket, objectKey, file, contentType);

        StorageDocumentDO entity = new StorageDocumentDO();
        entity.setTenantId(tenantId);
        entity.setKbId(kbId);
        entity.setVisibility(resolvedVisibility.name());
        entity.setDeptId(request.getDeptId());
        entity.setUserId(request.getUserId());
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
            // 元数据保存失败时删除刚上传的对象，避免对象存储残留业务不可见文件
            save(entity);
        } catch (RuntimeException ex) {
            objectStorageClient.deleteObject(bucket, objectKey);
            throw ex;
        }
        if (Boolean.TRUE.equals(request.getAutoIndex())) {
            try {
                // 自动索引失败不回滚上传结果，只把索引状态标记为 INDEX_FAILED
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
     * <p>
     * 查询前会同步最新索引执行状态，避免列表展示旧的 INDEXING / INDEX_FAILED 状态
     *
     * @param request 分页查询请求
     * @return 分页对象
     */
    @Override
    public Page<StorageDocumentDO> pageDocuments(StorageDocumentPageRequest request) {
        String tenantId = request.getTenantId();
        String kbId = request.getKbId();
        validateScope(tenantId, kbId);
        Page<StorageDocumentDO> result = page(Page.of(resolveCurrent(request.getCurrent()),
                        resolveSize(request.getSize())),
                query(tenantId, kbId, request.getVisibility(), request.getDeptId(), request.getUserId(),
                        request.getIndexStatus(), request.getKeyword()));
        result.getRecords().forEach(this::syncIndexStatus);
        return result;
    }

    /**
     * 下载对象存储文档
     *
     * @param tenantId   租户 ID
     * @param kbId       知识库 ID
     * @param documentId 文档 ID
     * @return 下载结果
     */
    @Override
    public StorageDocumentDownloadResponse download(String tenantId, String kbId, String documentId) {
        StorageDocumentDO entity = getByDocumentId(tenantId, kbId, documentId);
        return download(entity);
    }

    /**
     * 按全局 documentId 下载对象存储文档
     *
     * <p>
     * 普通 RAG 响应只返回 documentId，下载接口在业务层通过唯一约束定位真实对象存储文件
     *
     * @param documentId 文档 ID
     * @return 下载结果
     */
    @Override
    public StorageDocumentDownloadResponse download(String documentId) {
        StorageDocumentDO entity = getByDocumentId(documentId);
        return download(entity);
    }

    /**
     * 提交对象存储文档索引执行
     *
     * @param tenantId   租户 ID
     * @param kbId       知识库 ID
     * @param documentId 文档 ID
     * @return 更新后的文档元数据
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StorageDocumentDO index(String tenantId, String kbId, String documentId) {
        StorageDocumentDO entity = getByDocumentId(tenantId, kbId, documentId);
        // 手动索引复用上传后的元数据，避免接口层重复拼装对象存储读取参数
        indexSavedDocument(entity);
        return entity;
    }

    /**
     * 提交已保存文档的索引执行
     *
     * <p>
     * 索引服务异步执行 ETL，本方法只负责更新提交前状态和记录最新 runId
     *
     * @param entity 已保存的对象存储文档元数据
     */
    private void indexSavedDocument(StorageDocumentDO entity) {
        entity.setIndexStatus(StorageDocumentIndexStatus.INDEXING.name());
        entity.setUpdatedAt(Instant.now());
        updateById(entity);
        try {
            // DocumentIndexingRequest 只表达知识库文档索引，scope 由 RAG metadata Transformer 固定写入 knowledge
            documentIndexingService.submit(DocumentIndexingRequest.builder()
                    .tenantId(entity.getTenantId())
                    .kbId(entity.getKbId())
                    .documentId(entity.getDocumentId())
                    .visibility(entity.getVisibility())
                    .deptId(entity.getDeptId())
                    .userId(entity.getUserId())
                    .documentType(entity.getDocumentType())
                    .source(entity.getSource())
                    .documentName(entity.getOriginalFilename())
                    .bucket(entity.getBucket())
                    .objectKey(entity.getObjectKey())
                    .build(), run -> {
                // beforeRun 在索引执行开始前回调，用于把 runId 绑定回业务文档
                entity.setLatestIndexingRunId(run.runId());
                entity.setUpdatedAt(Instant.now());
                updateById(entity);
            });
        } catch (RuntimeException ex) {
            // 提交失败说明本轮索引没有进入 RUNNING，立即回写失败状态
            entity.setIndexStatus(StorageDocumentIndexStatus.INDEX_FAILED.name());
            entity.setUpdatedAt(Instant.now());
            updateById(entity);
            throw ex;
        }
    }

    /**
     * 同步索引执行状态
     *
     * <p>
     * 列表查询时根据最新 runId 读取索引服务状态，并回写对象存储文档表
     *
     * @param entity 文档元数据
     */
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
            // 这里只同步状态，不修改 chunkCount，chunkCount 由 StorageDocumentIndexingRunObserver 统一处理
            updateById(entity);
        }
    }

    /**
     * 构造对象存储下载结果
     *
     * <p>
     * 文件名、内容类型和大小来自元数据表，文件流按 bucket 与 objectKey 从对象存储读取
     *
     * @param entity 文档元数据
     * @return 下载结果
     */
    private StorageDocumentDownloadResponse download(StorageDocumentDO entity) {
        return new StorageDocumentDownloadResponse(entity.getOriginalFilename(), entity.getContentType(),
                entity.getFileSize(), objectStorageClient.getObject(entity.getBucket(), entity.getObjectKey()));
    }

    /**
     * 生成全局文档 ID
     *
     * <p>
     * IdWorker.getIdStr() 是 MyBatis Plus 提供的雪花 ID 字符串生成工具
     * 数据库 uk_storage_document_document 唯一约束作为最终兜底，避免极端部署配置错误导致重复 documentId
     *
     * @return 全局文档 ID
     */
    private String newDocumentId() {
        return IdWorker.getIdStr();
    }

    /**
     * 按租户、知识库和文档 ID 查询未删除文档
     *
     * @param tenantId   租户 ID
     * @param kbId       知识库 ID
     * @param documentId 文档 ID
     * @return 文档元数据
     */
    private StorageDocumentDO getByDocumentId(String tenantId, String kbId, String documentId) {
        validateScope(tenantId, kbId);
        Assert.hasText(documentId, "documentId must not be blank");
        StorageDocumentDO entity = getOne(new LambdaQueryWrapper<StorageDocumentDO>()
                .eq(StorageDocumentDO::getTenantId, tenantId)
                .eq(StorageDocumentDO::getKbId, kbId)
                .eq(StorageDocumentDO::getDocumentId, documentId)
                .eq(StorageDocumentDO::getDeleted, Boolean.FALSE));
        if (entity == null) {
            throw new IllegalArgumentException("storage document not found: " + documentId);
        }
        return entity;
    }

    /**
     * 按全局 documentId 查询未删除文档
     *
     * <p>
     * 该入口服务于 RAG references 下载，依赖 documentId 数据库唯一约束
     *
     * @param documentId 文档 ID
     * @return 文档元数据
     */
    private StorageDocumentDO getByDocumentId(String documentId) {
        Assert.hasText(documentId, "documentId must not be blank");
        StorageDocumentDO entity = getOne(new LambdaQueryWrapper<StorageDocumentDO>()
                .eq(StorageDocumentDO::getDocumentId, documentId)
                .eq(StorageDocumentDO::getDeleted, Boolean.FALSE));
        if (entity == null) {
            throw new IllegalArgumentException("storage document not found: " + documentId);
        }
        return entity;
    }

    /**
     * 构造分页查询条件
     *
     * @param tenantId    租户 ID
     * @param kbId        知识库 ID
     * @param visibility  文档可见性
     * @param deptId      部门 ID
     * @param userId      用户 ID
     * @param indexStatus 索引状态
     * @param keyword     文件名关键字
     * @return MyBatis Plus 查询条件
     */
    private LambdaQueryWrapper<StorageDocumentDO> query(String tenantId,
                                                        String kbId,
                                                        String visibility,
                                                        String deptId,
                                                        String userId,
                                                        String indexStatus,
                                                        String keyword) {
        return new LambdaQueryWrapper<StorageDocumentDO>()
                .eq(StorageDocumentDO::getTenantId, tenantId)
                .eq(StorageDocumentDO::getKbId, kbId)
                .eq(StorageDocumentDO::getDeleted, Boolean.FALSE)
                .eq(StringUtils.hasText(visibility), StorageDocumentDO::getVisibility, visibility)
                .eq(StringUtils.hasText(deptId), StorageDocumentDO::getDeptId, deptId)
                .eq(StringUtils.hasText(userId), StorageDocumentDO::getUserId, userId)
                .eq(StringUtils.hasText(indexStatus), StorageDocumentDO::getIndexStatus, indexStatus)
                .like(StringUtils.hasText(keyword), StorageDocumentDO::getOriginalFilename, keyword)
                .orderByDesc(StorageDocumentDO::getCreatedAt)
                .orderByDesc(StorageDocumentDO::getId);
    }

    /**
     * 上传文件内容到对象存储
     *
     * @param bucket      bucket
     * @param objectKey   object key
     * @param file        上传文件
     * @param contentType 内容类型
     * @return 对象存储写入结果
     */
    private StoredObject putObject(String bucket, String objectKey, MultipartFile file, String contentType) {
        try (InputStream inputStream = file.getInputStream()) {
            return objectStorageClient.putObject(bucket, objectKey, inputStream, file.getSize(), contentType);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to upload object storage document", ex);
        }
    }

    /**
     * 计算上传文件 SHA-256
     *
     * <p>
     * hash 用于对象路径、重复排查和审计，不作为业务 documentId
     *
     * @param file 上传文件
     * @return SHA-256 十六进制字符串
     */
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

    /**
     * 构造对象存储路径
     *
     * <p>
     * 路径包含 tenantId、kbId、月份、documentId 和文件 hash，便于人工定位和排查
     *
     * @param tenantId         租户 ID
     * @param kbId             知识库 ID
     * @param documentId       文档 ID
     * @param fileSha256       文件 SHA-256
     * @param originalFilename 原始文件名
     * @return 对象存储 object key
     */
    private String objectKey(String tenantId,
                             String kbId,
                             String documentId,
                             String fileSha256,
                             String originalFilename) {
        String datePath = OBJECT_DATE_FORMATTER.format(Instant.now());
        String extension = fileExtension(originalFilename);
        return "storage/%s/%s/%s/%s/%s%s".formatted(tenantId, kbId, datePath, documentId, fileSha256,
                extension);
    }

    /**
     * 提取文件扩展名
     *
     * @param filename 文件名
     * @return 小写扩展名
     */
    private String fileExtension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index).toLowerCase();
    }

    /**
     * 解析浏览器上传的原始文件名
     *
     * <p>
     * 浏览器可能上传带路径的文件名，这里只保留最后一级文件名
     *
     * @param file 上传文件
     * @return 原始文件名
     */
    private String resolveOriginalFilename(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename)) {
            return "upload.bin";
        }
        return filename.replace("\\", "/").substring(filename.replace("\\", "/").lastIndexOf('/') + 1);
    }

    /**
     * 解析上传文件 Content-Type
     *
     * @param file 上传文件
     * @return 内容类型
     */
    private String resolveContentType(MultipartFile file) {
        return StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream";
    }

    /**
     * 转换上传响应
     *
     * @param entity 文档元数据
     * @return 上传响应
     */
    private StorageDocumentUploadResponse response(StorageDocumentDO entity) {
        return new StorageDocumentUploadResponse(entity.getDocumentId(), entity.getOriginalFilename(),
                entity.getVisibility(), entity.getDeptId(), entity.getUserId(), entity.getBucket(),
                entity.getObjectKey(), entity.getFileSize(), entity.getFileSha256(), entity.getDocumentType(),
                entity.getIndexStatus(), entity.getChunkCount(), entity.getLatestIndexingRunId());
    }

    /**
     * 解析并校验文档可见性
     *
     * @param visibility 原始可见性
     * @param deptId     部门 ID
     * @param userId     用户 ID
     * @return 文档可见性
     */
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

    /**
     * 校验租户和知识库边界
     *
     * @param tenantId 租户 ID
     * @param kbId     知识库 ID
     */
    private void validateScope(String tenantId, String kbId) {
        Assert.hasText(tenantId, "tenantId must not be blank");
        Assert.hasText(kbId, "kbId must not be blank");
    }

    /**
     * 解析页码
     *
     * @param current 原始页码
     * @return 兜底后的页码
     */
    private long resolveCurrent(Long current) {
        return current == null || current < DEFAULT_CURRENT ? DEFAULT_CURRENT : current;
    }

    /**
     * 解析每页数量
     *
     * @param size 原始每页数量
     * @return 兜底并限制上限后的每页数量
     */
    private long resolveSize(Long size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
