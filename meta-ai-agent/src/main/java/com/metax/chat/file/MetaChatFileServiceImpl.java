package com.metax.chat.file;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.metax.chat.file.event.MetaChatFileIndexingEvent;
import com.metax.rag.etl.resource.MetaDocumentTypeResolver;
import com.metax.rag.model.MetadataKeys;
import com.metax.rag.retrieval.advisor.MetaContextFile;
import com.metax.rag.storage.ObjectStorageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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

    private final VectorStore vectorStore;

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 上传聊天文件并提交异步解析任务
     *
     * <p>
     * 本方法同步完成对象存储归档和文件元数据落库
     * OCR、chunk 切分和 VectorStore 写入由后台任务继续执行，前端通过 listFiles 轮询 parseStatus
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param chatId   会话 ID
     * @param files    上传文件
     * @return 已提交处理的文件状态列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<MetaChatFileItemResponse> uploadAndSubmitIndex(String tenantId, String userId, String chatId,
                                                               MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return List.of();
        }
        validateScope(tenantId, userId, chatId);
        List<MetaChatFileDO> uploadedFiles = Arrays.stream(files)
                .filter(Objects::nonNull)
                .map(file -> uploadOne(tenantId, userId, chatId, file))
                .toList();
        // 发布事务事件，监听器会在上传事务提交后触发异步索引执行器
        uploadedFiles.forEach(file -> eventPublisher.publishEvent(new MetaChatFileIndexingEvent(this,
                file.getFileId())));
        return uploadedFiles.stream()
                .map(this::fileItem)
                .toList();
    }

    /**
     * 查询当前会话全部未删除文件
     *
     * <p>
     * 前端展示需要看到 UPLOADED、PARSING、READY 和 PARSE_FAILED
     * 问答链路不要使用本方法，避免非 READY 文件进入上下文增强
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param chatId   会话 ID
     * @return 文件状态列表
     */
    @Override
    public List<MetaChatFileItemResponse> listFiles(String tenantId, String userId, String chatId) {
        validateScope(tenantId, userId, chatId);
        return list(new LambdaQueryWrapper<MetaChatFileDO>()
                .eq(MetaChatFileDO::getTenantId, tenantId)
                .eq(MetaChatFileDO::getUserId, userId)
                .eq(MetaChatFileDO::getChatId, chatId)
                .eq(MetaChatFileDO::getDeleted, Boolean.FALSE)
                .orderByDesc(MetaChatFileDO::getCreatedAt))
                .stream()
                .map(this::fileItem)
                .toList();
    }

    /**
     * 查询当前会话可用文件
     *
     * <p>
     * 只返回 READY 且未删除的文件，用于文件池展示或内部显式读取
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
            // 显式文件查询不接受空集合，避免调用方误以为会自动使用历史 READY 文件
            return List.of();
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
     * 上传单个会话文件并保存元数据
     *
     * <p>
     * 文件先归档到对象存储，再写入元数据表
     * 后台索引任务会基于这里生成的 fileId 继续执行解析和临时向量索引
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param chatId   会话 ID
     * @param file     上传文件
     * @return 已保存的文件元数据
     */
    private MetaChatFileDO uploadOne(String tenantId,
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
        return entity;
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
     * 转换为对 RAG 模块暴露的会话文件上下文
     *
     * @param file 会话文件元数据
     * @return 会话文件上下文
     */
    private MetaContextFile contextFile(MetaChatFileDO file) {
        return new MetaContextFile(file.getFileId(), file.getOriginalFilename(), file.getDocumentType());
    }

    /**
     * 转换为前端展示的会话文件状态
     *
     * @param file 会话文件元数据
     * @return 会话文件展示响应
     */
    private MetaChatFileItemResponse fileItem(MetaChatFileDO file) {
        return new MetaChatFileItemResponse(file.getFileId(), file.getOriginalFilename(), file.getDocumentType(),
                file.getParseStatus(), file.getChunkCount(), file.getCreatedAt(), file.getUpdatedAt());
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

}
