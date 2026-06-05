package com.metax.rag.etl.reader;

import com.metax.rag.config.RagProperties;
import com.metax.rag.etl.resource.MetaDocumentResource;
import org.springframework.ai.document.DocumentReader;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * PaddleOcrPdfDocumentReaderStrategy .
 *
 * <p>
 * PDF OCR Reader 策略，专门处理扫描版 PDF
 * 策略 Bean 必须保持无状态，不能保存请求级 Resource
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/5
 */
// PDF OCR 必须优先于 Tika 兜底策略，否则扫描件 PDF 会被 Tika 当成普通复杂文档处理
@Order(0)
@Component
public class PaddleOcrPdfDocumentReaderStrategy implements MetaDocumentReaderStrategy {

    private static final String PDF = "pdf";

    private static final String PROVIDER_PADDLE = "paddle";

    private final RagProperties properties;

    private final PaddleOcrClient paddleOcrClient;

    public PaddleOcrPdfDocumentReaderStrategy(RagProperties properties, PaddleOcrClient paddleOcrClient) {
        this.properties = properties;
        this.paddleOcrClient = paddleOcrClient;
    }

    /**
     * 是否支持 PDF OCR
     *
     * <p>
     * 同时满足 OCR 开关启用、provider 为 paddle、documentType 为 pdf 时才接管 Reader
     * 关闭 OCR 后 pdf 会回到 Tika 兜底策略，适合临时处理文字型 PDF 或排查 OCR 服务问题
     *
     * @param documentType 文档类型
     * @return 是否支持
     */
    @Override
    public boolean supports(String documentType) {
        RagProperties.Ocr ocr = properties.getOcr();
        return ocr.isEnabled()
                && PROVIDER_PADDLE.equals(normalize(ocr.getProvider()))
                && PDF.equals(normalize(documentType));
    }

    /**
     * 创建 PaddleOCR PDF Reader
     *
     * <p>
     * 这里创建的是请求级 Reader，Resource 只保存在 Reader 实例中，不保存到策略 Bean 字段
     *
     * @param documentResource 文档资源
     * @return DocumentReader
     */
    @Override
    public DocumentReader create(MetaDocumentResource documentResource) {
        return new PaddleOcrPdfDocumentReader(documentResource, paddleOcrClient);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
