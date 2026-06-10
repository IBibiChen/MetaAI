package com.metax.chat.file;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.metax.rag.etl.reader.MetaDocumentReaderFactory;
import com.metax.rag.etl.resource.MetaDocumentResource;
import com.metax.rag.etl.transformer.MetaDocumentTransformerFactory;
import com.metax.rag.model.MetadataKeys;
import com.metax.rag.pipeline.MetaVectorStoreWriter;
import com.metax.rag.storage.ObjectStorageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.AbstractResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MetaChatFileIndexingService .
 *
 * <p>
 * 会话文件索引服务，负责文件解析、状态流转和 scope = session 的临时向量索引
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetaChatFileIndexingService extends ServiceImpl<MetaChatFileMapper, MetaChatFileDO> {

    private final ObjectStorageClient objectStorageClient;

    private final MetaDocumentReaderFactory documentReaderFactory;

    private final MetaDocumentTransformerFactory documentTransformerFactory;

    private final MetaVectorStoreWriter vectorStoreWriter;

    /**
     * 按文件 ID 执行会话文件临时索引
     *
     * <p>
     * 本方法由异步执行器调用，文件记录已经在上传事务提交后可见
     * 方法内部只处理单个 fileId 的解析、临时向量写入和解析状态流转
     *
     * @param fileId 文件 ID
     */
    public void index(String fileId) {
        log.info("开始执行会话文件索引：fileId = {}", fileId);
        MetaChatFileDO file = getByFileId(fileId);
        if (file == null) {
            log.warn("会话文件索引任务跳过：fileId = {}，reason = file_not_found", fileId);
            return;
        }
        try {
            markParsing(file);
            int chunkCount = indexFile(file);
            markReady(file, chunkCount);
        } catch (RuntimeException ex) {
            markParseFailed(file, Instant.now());
            log.error("会话文件索引任务失败：chatId = {}，fileId = {}，fileName = {}",
                    file.getChatId(), file.getFileId(), file.getOriginalFilename(), ex);
        }
    }

    /**
     * 按 fileId 查询会话文件元数据
     *
     * <p>
     * 后台索引任务通过 fileId 重新加载文件，避免跨线程复用上传事务中的实体状态
     *
     * @param fileId 文件 ID
     * @return 文件元数据，找不到时返回 null
     */
    private MetaChatFileDO getByFileId(String fileId) {
        if (!StringUtils.hasText(fileId)) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<MetaChatFileDO>()
                .eq(MetaChatFileDO::getFileId, fileId)
                .eq(MetaChatFileDO::getDeleted, Boolean.FALSE), false);
    }

    /**
     * 标记文件进入解析中状态
     *
     * @param file 会话文件元数据
     */
    private void markParsing(MetaChatFileDO file) {
        file.setParseStatus(MetaChatFileStatus.PARSING.name());
        file.setUpdatedAt(Instant.now());
        updateById(file);
    }

    /**
     * 标记文件索引完成
     *
     * @param file       会话文件元数据
     * @param chunkCount 写入向量库的 chunk 数量
     */
    private void markReady(MetaChatFileDO file, int chunkCount) {
        file.setParseStatus(MetaChatFileStatus.READY.name());
        file.setChunkCount(chunkCount);
        file.setUpdatedAt(Instant.now());
        updateById(file);
    }

    /**
     * 标记文件解析失败
     *
     * @param file     会话文件元数据
     * @param failedAt 失败时间
     */
    private void markParseFailed(MetaChatFileDO file, Instant failedAt) {
        file.setParseStatus(MetaChatFileStatus.PARSE_FAILED.name());
        file.setUpdatedAt(failedAt);
        updateById(file);
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
        // 删除只按当前文件执行一次，避免后续分批写入时误删已经写入的新 chunk
        vectorStoreWriter.delete(fileDeleteFilter(file));
        // 写入统一走项目级批次控制，兼容不同 embedding provider 的单次输入条数限制
        vectorStoreWriter.write(documents);
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
        metadata.put(MetadataKeys.CONTENT_HASH, contentHash(document.getText()));
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
                builder.and(
                        builder.eq(MetadataKeys.SCOPE, MetadataKeys.SCOPE_SESSION),
                        builder.eq(MetadataKeys.TENANT_ID, file.getTenantId())),
                builder.and(
                        builder.eq(MetadataKeys.USER_ID, file.getUserId()),
                        builder.and(builder.eq(MetadataKeys.CHAT_ID, file.getChatId()),
                                builder.eq(MetadataKeys.FILE_ID, file.getFileId())))
        ).build();
    }

    /**
     * 计算 chunk 内容 hash
     *
     * <p>
     * 会话文件与知识库文档使用同一套 contentHash 语义，便于后续审计和增量索引扩展
     *
     * @param text chunk 文本
     * @return contentHash
     */
    private String contentHash(String text) {
        String normalizedText = text == null ? "" : text;
        return org.springframework.util.DigestUtils.md5DigestAsHex(normalizedText.getBytes(StandardCharsets.UTF_8));
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
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
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
