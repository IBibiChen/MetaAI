package com.metax.rag.etl.transformer;

import com.metax.rag.config.RagProperties;
import com.metax.rag.indexing.DocumentIndexingRequest;
import com.metax.rag.model.MetadataKeys;
import org.springframework.ai.document.DefaultContentFormatter;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.transformer.ContentFormatTransformer;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MetaDocumentTransformerFactory .
 *
 * <p>
 * MetaAI DocumentTransformer 工厂，负责集中创建 RAG 文档索引链路中的 Transformer
 * 绑定 DocumentIndexingRequest 的 Transformer 每次创建，官方无请求状态 Transformer 也在这里统一配置
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
@Component
public class MetaDocumentTransformerFactory {

    private static final List<String> EXCLUDED_FORMAT_METADATA_KEYS = List.of(
            MetadataKeys.SCOPE,
            MetadataKeys.TENANT_ID,
            MetadataKeys.KB_ID,
            MetadataKeys.VISIBILITY,
            MetadataKeys.DEPT_ID,
            MetadataKeys.USER_ID,
            MetadataKeys.CONVERSATION_ID,
            MetadataKeys.FILE_ID,
            MetadataKeys.DOCUMENT_ID,
            MetadataKeys.DOCUMENT_TYPE,
            MetadataKeys.SOURCE,
            MetadataKeys.DOCUMENT_NAME,
            MetadataKeys.FILE_NAME,
            MetadataKeys.CHUNK_ID,
            MetadataKeys.CHUNK_INDEX,
            MetadataKeys.CONTENT_HASH,
            MetadataKeys.CREATED_AT
    );

    private final RagProperties properties;

    public MetaDocumentTransformerFactory(RagProperties properties) {
        this.properties = properties;
    }

    /**
     * 创建文档级 metadata 增强 Transformer
     *
     * @param request RAG 文档索引请求
     * @return DocumentTransformer
     */
    public DocumentTransformer documentMetadataEnricher(DocumentIndexingRequest request) {
        return new MetaDocumentMetadataTransformer(request);
    }

    /**
     * 创建 token chunk 切分 Transformer
     *
     * <p>
     * TokenTextSplitter 是 Spring AI 官方 DocumentTransformer
     * 把带文档级 metadata 的原始 Document 切成多个 chunk Document
     * Spring AI 的 splitter 会把原 Document 的 metadata 带到切分后的 chunk 上
     * -
     * 当前参数来自 metax.ai.rag.chunk，避免切分参数硬编码在 Pipeline 中
     *
     * @return DocumentTransformer
     */
    public DocumentTransformer tokenTextSplitter() {
        return TokenTextSplitter.builder()
                .withChunkSize(properties.getChunk().getSize())
                .withMinChunkSizeChars(properties.getChunk().getMinChars())
                .withMinChunkLengthToEmbed(properties.getChunk().getMinLengthToEmbed())
                .withMaxNumChunks(properties.getChunk().getMaxNumChunks())
                .withKeepSeparator(properties.getChunk().isKeepSeparator())
                .build();
    }

    /**
     * 创建 chunk 级 metadata 增强 Transformer
     *
     * @param request RAG 文档索引请求
     * @return DocumentTransformer
     */
    public DocumentTransformer chunkMetadataEnricher(DocumentIndexingRequest request) {
        return new MetaChunkMetadataTransformer(request);
    }

    /**
     * 创建内容格式化 Transformer
     *
     * <p>
     * ContentFormatTransformer 不修改 Document text，只设置 ContentFormatter
     * 技术 metadata 保留在 Document.metadata 中，但不参与 EMBED / INFERENCE 文本格式化
     *
     * @return DocumentTransformer
     */
    public DocumentTransformer contentFormatTransformer() {
        DefaultContentFormatter formatter = DefaultContentFormatter.builder()
                .withExcludedEmbedMetadataKeys(EXCLUDED_FORMAT_METADATA_KEYS)
                .withExcludedInferenceMetadataKeys(EXCLUDED_FORMAT_METADATA_KEYS)
                .build();
        return new ContentFormatTransformer(formatter);
    }
}
