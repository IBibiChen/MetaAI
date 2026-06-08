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
 * PaddleOcrDocumentReader .
 *
 * <p>
 * OCR Reader，通过 PaddleOCR 把扫描版 PDF 或图片识别为文本 Document
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
public class PaddleOcrDocumentReader implements DocumentReader {

    private static final Logger log = LoggerFactory.getLogger(PaddleOcrDocumentReader.class);

    private final MetaDocumentResource documentResource;

    private final PaddleOcrClient paddleOcrClient;

    public PaddleOcrDocumentReader(MetaDocumentResource documentResource, PaddleOcrClient paddleOcrClient) {
        this.documentResource = Objects.requireNonNull(documentResource, "MetaDocumentResource must not be null");
        this.paddleOcrClient = Objects.requireNonNull(paddleOcrClient, "PaddleOcrClient must not be null");
    }

    /**
     * 读取 OCR 文本
     *
     * <p>
     * PaddleOCR 返回的每个结果都会转换为一个 Spring AI Document
     * PDF 通常按页返回，图片通常返回单个结果
     * 保持页级 Document 可以在快照和后续 chunk 中保留更清晰的来源定位
     *
     * @return OCR 识别后的 Document 列表
     */
    @Override
    public List<Document> get() {
        log.info("开始读取 OCR 文档：source = {}，documentType = {}",
                documentResource.source(), documentResource.documentType());
        List<String> pages = paddleOcrClient.recognize(documentResource.resource(), documentResource.documentType());
        List<Document> documents = new ArrayList<>();
        for (int index = 0; index < pages.size(); index++) {
            String text = pages.get(index);
            if (!StringUtils.hasText(text)) {
                // OCR 空页或空图片结果不生成 Document，避免无意义空 chunk 进入后续切分和 embedding
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
            // ocrFileType 用于排查 PaddleX /ocr 调用时走的是 PDF 还是图片分支
            metadata.put("ocrFileType", ocrFileType(documentResource.documentType()));
            documents.add(Document.builder()
                    .text(text.trim())
                    .metadata(metadata)
                    .build());
        }
        if (documents.isEmpty()) {
            // 所有结果都为空时必须让索引失败，不能把扫描件 PDF 或图片伪装成已成功入库
            throw new IllegalStateException("PaddleOCR returned no readable document text");
        }
        log.info("OCR 文档读取完成：source = {}，items = {}，documents = {}",
                documentResource.source(), pages.size(), documents.size());
        return documents;
    }

    private String ocrFileType(String documentType) {
        return "pdf".equalsIgnoreCase(documentType) ? "pdf" : "image";
    }
}
