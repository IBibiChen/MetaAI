package com.metax.rag.etl.reader;

import com.metax.rag.etl.resource.MetaDocumentResource;
import com.metax.rag.model.MetadataKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * PaddleOcrPdfDocumentReader .
 *
 * <p>
 * 扫描版 PDF Reader，通过 PaddleOCR 把页面图片识别为文本 Document
 * 该 Reader 绑定请求级 Resource，不注册为 Spring 单例 Bean
 *
 * <p>
 * Reader 阶段只负责 OCR 解析和页级 metadata
 * 租户、知识库、documentId、documentType 等业务 metadata 仍由 MetaDocumentMetadataTransformer 补齐
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/5
 */
public class PaddleOcrPdfDocumentReader implements DocumentReader {

    private static final Logger log = LoggerFactory.getLogger(PaddleOcrPdfDocumentReader.class);

    private final MetaDocumentResource documentResource;

    private final PaddleOcrClient paddleOcrClient;

    public PaddleOcrPdfDocumentReader(MetaDocumentResource documentResource, PaddleOcrClient paddleOcrClient) {
        this.documentResource = Objects.requireNonNull(documentResource, "MetaDocumentResource must not be null");
        this.paddleOcrClient = Objects.requireNonNull(paddleOcrClient, "PaddleOcrClient must not be null");
    }

    /**
     * 读取 PDF OCR 文本
     *
     * <p>
     * PaddleOCR 返回的每一页文本都会转换为一个 Spring AI Document
     * 保持页级 Document 可以在快照和后续 chunk 中保留更清晰的来源定位
     *
     * @return OCR 识别后的 Document 列表
     */
    @Override
    public List<Document> get() {
        log.info("开始读取 PDF OCR 文档：source = {}，documentType = {}",
                documentResource.source(), documentResource.documentType());
        List<String> pages = paddleOcrClient.recognizePdf(documentResource.resource());
        List<Document> documents = new ArrayList<>();
        for (int index = 0; index < pages.size(); index++) {
            String text = pages.get(index);
            if (!StringUtils.hasText(text)) {
                // OCR 空页不生成 Document，避免无意义空 chunk 进入后续切分和 embedding
                continue;
            }
            HashMap<String, Object> metadata = new HashMap<>();
            // source 只用于保留 Reader 阶段来源，最终来源字段会在 metadata Transformer 中按请求统一覆盖
            metadata.put(MetadataKeys.SOURCE, documentResource.source());
            // pageIndex 使用 0 基索引，方便程序内部排序和定位
            metadata.put("pageIndex", index);
            // pageNumber 使用 1 基页码，方便前端展示和人工排查
            metadata.put("pageNumber", index + 1);
            // ocrProvider 用于排查该 Document 来自 OCR Reader，而不是 Tika 或其他 Reader
            metadata.put("ocrProvider", "paddleocr");
            documents.add(Document.builder()
                    .text(text.trim())
                    .metadata(metadata)
                    .build());
        }
        if (documents.isEmpty()) {
            // 所有页面都为空时必须让索引失败，不能把扫描件 PDF 伪装成已成功入库
            throw new IllegalStateException("PaddleOCR returned no readable PDF page text");
        }
        log.info("PDF OCR 文档读取完成：source = {}，pages = {}，documents = {}",
                documentResource.source(), pages.size(), documents.size());
        return documents;
    }
}
