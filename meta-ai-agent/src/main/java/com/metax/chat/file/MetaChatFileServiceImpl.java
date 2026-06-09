package com.metax.chat.file;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.metax.rag.etl.reader.MetaDocumentReaderFactory;
import com.metax.rag.etl.resource.MetaDocumentResource;
import com.metax.rag.etl.resource.MetaDocumentTypeResolver;
import com.metax.rag.etl.transformer.MetaDocumentTransformerFactory;
import com.metax.rag.model.MetadataKeys;
import com.metax.rag.retrieval.advisor.MetaContextFile;
import com.metax.rag.storage.ObjectStorageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.AbstractResource;
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
import java.util.*;

/**
 * MetaChatFileServiceImpl .
 *
 * <p>
 * 聊天文件服务实现，文件只写入会话级临时索引，不进入知识库文档表
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetaChatFileServiceImpl extends ServiceImpl<MetaChatFileMapper, MetaChatFileDO>
        implements MetaChatFileService {

    /**
     * 单个会话文件最大上传大小
     */
    private static final long MAX_UPLOAD_SIZE = 100 * 1024 * 1024L;

    /**
     * 会话文件上下文默认召回数量
     */
    private static final int DEFAULT_TOP_K = 8;

    /**
     * 会话文件默认不做相似度阈值截断，优先保证用户上传文件可被用于回答
     */
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.0;

    /**
     * 对象存储路径按月份分桶，避免单目录对象过多
     */
    private static final DateTimeFormatter OBJECT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM")
            .withZone(ZoneId.systemDefault());

    private final ObjectStorageClient objectStorageClient;

    private final MetaDocumentTypeResolver documentTypeResolver;

    private final MetaDocumentReaderFactory documentReaderFactory;

    private final MetaDocumentTransformerFactory documentTransformerFactory;

    private final VectorStore vectorStore;

    /**
     * 上传并解析聊天文件
     *
     * <p>
     * 本方法同步完成对象存储归档、Reader 解析、chunk 切分和 VectorStore 写入
     * v1 选择同步链路，是为了让后续问答流可以立即通过 fileIds 引用已上传文件
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param chatId   会话 ID
     * @param files    上传文件
     * @return 已解析文件列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<MetaContextFile> uploadAndIndex(String tenantId, String userId, String chatId, MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return List.of();
        }
        validateScope(tenantId, userId, chatId);
        return Arrays.stream(files)
                .filter(Objects::nonNull)
                .map(file -> uploadAndIndexOne(tenantId, userId, chatId, file))
                .map(this::contextFile)
                .toList();
    }

    /**
     * 查询当前会话可用文件
     *
     * <p>
     * 只返回 READY 且未删除的文件，用于 fileIds 为空时的会话文件回退
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param chatId   会话 ID
     * @return 文件列表
     */
    @Override
    public List<MetaContextFile> readyFiles(String tenantId, String userId, String chatId) {
        validateScope(tenantId, userId, chatId);
        return list(new LambdaQueryWrapper<MetaChatFileDO>()
                .eq(MetaChatFileDO::getTenantId, tenantId)
                .eq(MetaChatFileDO::getUserId, userId)
                .eq(MetaChatFileDO::getChatId, chatId)
                .eq(MetaChatFileDO::getParseStatus, MetaChatFileStatus.READY.name())
                .eq(MetaChatFileDO::getDeleted, Boolean.FALSE)
                .orderByDesc(MetaChatFileDO::getCreatedAt))
                .stream()
                .map(this::contextFile)
                .toList();
    }

    /**
     * 按文件 ID 查询当前会话可用文件
     *
     * <p>
     * 显式 fileIds 必须全部属于当前 tenantId、userId、chatId 且处于 READY 状态
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param chatId   会话 ID
     * @param fileIds  文件 ID 列表
     * @return 文件列表
     */
    @Override
    public List<MetaContextFile> readyFiles(String tenantId, String userId, String chatId, List<String> fileIds) {
        validateScope(tenantId, userId, chatId);
        List<String> resolvedFileIds = normalizeFileIds(fileIds);
        if (resolvedFileIds.isEmpty()) {
            // 空 fileIds 的语义由上层定义为回退当前会话 READY 文件
            return readyFiles(tenantId, userId, chatId);
        }
        List<MetaContextFile> files = list(new LambdaQueryWrapper<MetaChatFileDO>()
                .eq(MetaChatFileDO::getTenantId, tenantId)
                .eq(MetaChatFileDO::getUserId, userId)
                .eq(MetaChatFileDO::getChatId, chatId)
                .eq(MetaChatFileDO::getParseStatus, MetaChatFileStatus.READY.name())
                .eq(MetaChatFileDO::getDeleted, Boolean.FALSE)
                .in(MetaChatFileDO::getFileId, resolvedFileIds)
                .orderByDesc(MetaChatFileDO::getCreatedAt))
                .stream()
                .map(this::contextFile)
                .toList();
        if (files.size() != resolvedFileIds.size()) {
            // 缺失任何一个 fileId 都说明文件不可用或越权，直接失败比静默忽略更安全
            throw new IllegalArgumentException("fileIds 包含不可用或无权访问的会话文件");
        }
        return files;
    }

    /**
     * 检索聊天文件内容
     *
     * <p>
     * 仅在当前会话、当前用户、当前租户范围内检索 Service 层传入的文件集合
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param chatId   会话 ID
     * @param files    文件列表
     * @param query    用户问题
     * @return 命中的 chunk
     */
    @Override
    public List<Document> retrieve(String tenantId,
                                   String userId,
                                   String chatId,
                                   List<MetaContextFile> files,
                                   String query) {
        validateScope(tenantId, userId, chatId);
        Assert.hasText(query, "query must not be blank");
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        // 向量检索过滤条件同时包含租户、用户、会话和 fileId，避免命中其他会话文件
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(DEFAULT_TOP_K)
                .similarityThreshold(DEFAULT_SIMILARITY_THRESHOLD)
                .filterExpression(fileFilter(tenantId, userId, chatId, files))
                .build();
        return vectorStore.similaritySearch(request);
    }

    /**
     * 上传并索引单个会话文件
     *
     * <p>
     * 文件先归档到对象存储，再写入元数据表，最后同步写入 scope = session 的临时向量索引
     * 解析失败时回写 PARSE_FAILED，并删除本次已经归档的对象，避免无效文件继续被会话引用
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param chatId   会话 ID
     * @param file     上传文件
     * @return 已完成索引的文件元数据
     */
    private MetaChatFileDO uploadAndIndexOne(String tenantId,
                                             String userId,
                                             String chatId,
                                             MultipartFile file) {
        validateFile(file);
        String fileId = IdWorker.getIdStr();
        String originalFilename = resolveOriginalFilename(file);
        String documentType = documentTypeResolver.resolve(null, originalFilename);
        String fileSha256 = fileSha256(file);
        String bucket = objectStorageClient.defaultBucket();
        String objectKey = objectKey(tenantId, userId, chatId, fileId, fileSha256, originalFilename);
        String contentType = resolveContentType(file);
        putObject(bucket, objectKey, file, contentType);

        MetaChatFileDO entity = new MetaChatFileDO();
        entity.setFileId(fileId);
        entity.setTenantId(tenantId);
        entity.setUserId(userId);
        entity.setChatId(chatId);
        entity.setOriginalFilename(originalFilename);
        entity.setDocumentType(documentType);
        entity.setBucket(bucket);
        entity.setObjectKey(objectKey);
        entity.setContentType(contentType);
        entity.setFileSize(file.getSize());
        entity.setFileSha256(fileSha256);
        entity.setParseStatus(MetaChatFileStatus.UPLOADED.name());
        entity.setChunkCount(0);
        entity.setDeleted(Boolean.FALSE);
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        save(entity);

        try {
            entity.setParseStatus(MetaChatFileStatus.PARSING.name());
            entity.setUpdatedAt(Instant.now());
            updateById(entity);
            int chunkCount = indexFile(entity);
            entity.setParseStatus(MetaChatFileStatus.READY.name());
            entity.setChunkCount(chunkCount);
            entity.setUpdatedAt(Instant.now());
            updateById(entity);
            return entity;
        } catch (RuntimeException ex) {
            entity.setParseStatus(MetaChatFileStatus.PARSE_FAILED.name());
            entity.setUpdatedAt(Instant.now());
            updateById(entity);
            // 已经写入对象存储但未成功索引的文件不应继续占用会话上下文
            objectStorageClient.deleteObject(bucket, objectKey);
            throw ex;
        }
    }

    /**
     * 把会话文件写入临时向量索引
     *
     * <p>
     * 会话文件复用知识库 ETL 的 Reader、Splitter 和 ContentFormatter，保证 PDF / 图片 OCR、切分和格式化规则一致
     * 但 metadata 由当前类重新补齐为 scope = session，避免被普通知识库 RAG 召回
     *
     * @param file 会话文件元数据
     * @return 写入向量库的 chunk 数量
     */
    private int indexFile(MetaChatFileDO file) {
        MetaDocumentResource resource = new MetaDocumentResource(new ObjectStorageResource(file),
                file.getDocumentType(), file.getObjectKey());
        DocumentReader reader = documentReaderFactory.create(resource);
        List<Document> documents = reader.read();
        documents = documentTransformerFactory.tokenTextSplitter().transform(documents);
        documents = fileMetadataTransformer(file).transform(documents);
        documents = documentTransformerFactory.contentFormatTransformer().transform(documents);
        vectorStore.delete(fileDeleteFilter(file));
        vectorStore.write(documents);
        log.info("聊天文件临时索引完成：chatId = {}，fileId = {}，fileName = {}，chunks = {}",
                file.getChatId(), file.getFileId(), file.getOriginalFilename(),
                documents.size());
        return documents.size();
    }

    /**
     * 构造会话文件 metadata Transformer
     *
     * <p>
     * 这里不复用知识库文档 metadata Transformer，因为会话文件使用 fileId / fileName 语义
     *
     * @param file 会话文件元数据
     * @return 会话文件 metadata Transformer
     */
    private DocumentTransformer fileMetadataTransformer(MetaChatFileDO file) {
        return documents -> java.util.stream.IntStream.range(0, documents.size())
                .mapToObj(index -> withFileMetadata(documents.get(index), file, index))
                .toList();
    }

    /**
     * 为单个会话文件 chunk 补齐临时索引 metadata
     *
     * <p>
     * scope = session 表示会话文件上下文，fileId 是文件边界，chatId 是会话隔离边界
     * 这些字段必须同时写入，后续 Advisor 检索时才不会跨用户、跨会话或跨文件召回
     *
     * @param document Reader / Splitter 输出的 Document
     * @param file     会话文件元数据
     * @param index    chunk 序号
     * @return 补齐会话文件 metadata 的 Document
     */
    private Document withFileMetadata(Document document, MetaChatFileDO file, int index) {
        String chunkId = "%s:%s".formatted(file.getFileId(), index);
        Map<String, Object> metadata = new java.util.HashMap<>(document.getMetadata());
        metadata.put(MetadataKeys.SCOPE, MetadataKeys.SCOPE_SESSION);
        metadata.put(MetadataKeys.TENANT_ID, file.getTenantId());
        metadata.put(MetadataKeys.USER_ID, file.getUserId());
        metadata.put(MetadataKeys.CHAT_ID, file.getChatId());
        metadata.put(MetadataKeys.FILE_ID, file.getFileId());
        metadata.put(MetadataKeys.DOCUMENT_TYPE, file.getDocumentType());
        metadata.put(MetadataKeys.SOURCE, file.getObjectKey());
        metadata.put(MetadataKeys.FILE_NAME, file.getOriginalFilename());
        metadata.put(MetadataKeys.CHUNK_ID, chunkId);
        metadata.put(MetadataKeys.CHUNK_INDEX, index);
        metadata.put(MetadataKeys.CREATED_AT, file.getCreatedAt().toEpochMilli());
        Document enriched = Document.builder()
                .id(vectorStoreId(file, chunkId))
                .text(document.getText())
                .metadata(metadata)
                .score(document.getScore())
                .build();
        // Document builder 不会自动继承 contentFormatter，需要显式恢复
        enriched.setContentFormatter(document.getContentFormatter());
        return enriched;
    }

    /**
     * 构造单文件删除过滤表达式
     *
     * <p>
     * 重复索引同一个 fileId 前先删旧 chunk，避免向量库中出现同一文件的重复片段
     *
     * @param file 会话文件元数据
     * @return 单文件删除过滤表达式
     */
    private Filter.Expression fileDeleteFilter(MetaChatFileDO file) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        return builder.and(
                builder.eq(MetadataKeys.SCOPE, MetadataKeys.SCOPE_SESSION),
                builder.eq(MetadataKeys.FILE_ID, file.getFileId())
        ).build();
    }

    /**
     * 构造会话文件检索过滤表达式
     *
     * <p>
     * 会话文件检索必须同时限定 scope、tenantId、userId、chatId 和 fileId
     * 这里的 fileId 来自本次上传文件或当前会话 READY 文件，不能用知识库 documentId 替代
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param chatId   会话 ID
     * @param files    本次允许检索的会话文件
     * @return 会话文件检索过滤表达式
     */
    private Filter.Expression fileFilter(String tenantId,
                                         String userId,
                                         String chatId,
                                         List<MetaContextFile> files) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        List<Object> fileIds = files.stream()
                .map(MetaContextFile::fileId)
                .filter(StringUtils::hasText)
                .map(Object.class::cast)
                .toList();
        return builder.and(
                builder.and(
                        builder.eq(MetadataKeys.SCOPE, MetadataKeys.SCOPE_SESSION),
                        builder.eq(MetadataKeys.TENANT_ID, tenantId)),
                builder.and(
                        builder.eq(MetadataKeys.USER_ID, userId),
                        builder.and(builder.eq(MetadataKeys.CHAT_ID, chatId),
                                builder.in(MetadataKeys.FILE_ID, fileIds)))
        ).build();
    }

    /**
     * 生成向量库稳定 Document ID
     *
     * <p>
     * 使用 scope、tenantId、chatId 和 chunkId 生成确定性 UUID
     * 同一会话文件重复索引时 ID 稳定，Qdrant 等向量库也能接受标准 UUID 形式
     *
     * @param file    会话文件元数据
     * @param chunkId chunk ID
     * @return 向量库 Document ID
     */
    private String vectorStoreId(MetaChatFileDO file, String chunkId) {
        String seed = "%s:%s:%s:%s".formatted(MetadataKeys.SCOPE_SESSION, file.getTenantId(),
                file.getChatId(), chunkId);
        return UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    /**
     * 转换为对 RAG 模块暴露的会话文件上下文
     *
     * @param file 会话文件元数据
     * @return 会话文件上下文
     */
    private MetaContextFile contextFile(MetaChatFileDO file) {
        return new MetaContextFile(file.getFileId(), file.getOriginalFilename(), file.getDocumentType());
    }

    /**
     * 上传文件内容到对象存储
     *
     * @param bucket      bucket
     * @param objectKey   object key
     * @param file        上传文件
     * @param contentType 内容类型
     */
    private void putObject(String bucket, String objectKey, MultipartFile file, String contentType) {
        try (InputStream inputStream = file.getInputStream()) {
            objectStorageClient.putObject(bucket, objectKey, inputStream, file.getSize(), contentType);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to upload chat file", ex);
        }
    }

    /**
     * 计算上传文件 SHA-256
     *
     * <p>
     * hash 用于对象路径和后续排查，不作为会话文件唯一 ID
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
            throw new IllegalStateException("failed to read chat file", ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    /**
     * 构造会话文件对象存储路径
     *
     * <p>
     * 路径中包含 tenantId、userId、chatId、fileId 和 hash，便于排查和人工定位
     *
     * @param tenantId         租户 ID
     * @param userId           用户 ID
     * @param chatId           会话 ID
     * @param fileId           文件 ID
     * @param fileSha256       文件 SHA-256
     * @param originalFilename 原始文件名
     * @return 对象存储 object key
     */
    private String objectKey(String tenantId,
                             String userId,
                             String chatId,
                             String fileId,
                             String fileSha256,
                             String originalFilename) {
        String datePath = OBJECT_DATE_FORMATTER.format(Instant.now());
        String extension = fileExtension(originalFilename);
        return "chat-files/%s/%s/%s/%s/%s/%s%s".formatted(tenantId, userId, datePath,
                safePath(chatId), fileId, fileSha256, extension);
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
     * 解析原始文件名
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
     * 解析内容类型
     *
     * @param file 上传文件
     * @return 内容类型
     */
    private String resolveContentType(MultipartFile file) {
        return StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream";
    }

    /**
     * 规范化文件 ID 列表
     *
     * @param fileIds 原始文件 ID 列表
     * @return 去重后的非空文件 ID 列表
     */
    private List<String> normalizeFileIds(List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        return fileIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    /**
     * 校验会话文件作用域
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param chatId   会话 ID
     */
    private void validateScope(String tenantId, String userId, String chatId) {
        Assert.hasText(tenantId, "tenantId must not be blank");
        Assert.hasText(userId, "userId must not be blank");
        Assert.hasText(chatId, "chatId must not be blank");
    }

    /**
     * 校验上传文件
     *
     * @param file 上传文件
     */
    private void validateFile(MultipartFile file) {
        Assert.notNull(file, "file must not be null");
        if (file.isEmpty()) {
            throw new IllegalArgumentException("file must not be empty");
        }
        if (file.getSize() > MAX_UPLOAD_SIZE) {
            throw new IllegalArgumentException("file size must not exceed " + MAX_UPLOAD_SIZE);
        }
    }

    /**
     * 转换为对象存储路径安全片段
     *
     * @param value 原始值
     * @return 路径安全片段
     */
    private String safePath(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * 对象存储 Resource 适配器
     *
     * <p>
     * Spring AI Reader 统一消费 Resource，这里把对象存储文件流适配成 Resource
     */
    private class ObjectStorageResource extends AbstractResource {

        private final MetaChatFileDO file;

        private ObjectStorageResource(MetaChatFileDO file) {
            this.file = file;
        }

        @Override
        public String getDescription() {
            return "chat file object: " + file.getObjectKey();
        }

        @Override
        public String getFilename() {
            return file.getOriginalFilename();
        }

        @Override
        public InputStream getInputStream() {
            return objectStorageClient.getObject(file.getBucket(), file.getObjectKey());
        }
    }
}
