package com.metax.rag.etl.reader;

import com.metax.rag.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * PaddleOcrClient .
 *
 * <p>
 * PaddleOCR HTTP 客户端，负责调用本地 Docker OCR 服务并把响应解析为页面文本
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/5
 */
@Component
public class PaddleOcrClient {

    private static final Logger log = LoggerFactory.getLogger(PaddleOcrClient.class);

    /**
     * PaddleOCR / PaddleX OCR 服务中 0 表示 PDF 文件
     */
    private static final int PDF_FILE_TYPE = 0;

    private final RagProperties properties;

    public PaddleOcrClient(RagProperties properties) {
        this.properties = properties;
    }

    /**
     * 识别 PDF 文本
     *
     * <p>
     * 请求字段按 PaddleX OCR serving 约定传入
     * file 是 PDF 文件 Base64，fileType = 0 表示 PDF，visualize 控制是否返回可视化结果
     * RAG 入库只消费 OCR 文本，不消费可视化图片
     *
     * @param resource PDF 资源
     * @return 按页返回的 OCR 文本
     */
    public List<String> recognizePdf(Resource resource) {
        Objects.requireNonNull(resource, "Resource must not be null");
        RagProperties.Ocr ocr = properties.getOcr();
        long start = System.nanoTime();
        String filename = resource.getFilename();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("file", encode(resource));
        request.put("fileType", PDF_FILE_TYPE);
        request.put("visualize", ocr.isVisualize());

        log.info("开始调用 PaddleOCR：filename = {}，baseUrl = {}，endpoint = {}，fileType = {}，visualize = {}",
                filename, ocr.getBaseUrl(), ocr.getEndpoint(), PDF_FILE_TYPE, ocr.isVisualize());
        Map<?, ?> response;
        try {
            response = restClient(ocr).post()
                    .uri(ocr.getEndpoint())
                    .body(request)
                    .retrieve()
                    .body(Map.class);
        } catch (RuntimeException ex) {
            log.error("PaddleOCR 调用失败：filename = {}，baseUrl = {}，endpoint = {}，costMs = {}",
                    filename, ocr.getBaseUrl(), ocr.getEndpoint(), costMs(start), ex);
            throw ex;
        }
        List<String> pages = parsePages(response);
        if (pages.isEmpty()) {
            log.warn("PaddleOCR 返回空文本：filename = {}，costMs = {}", filename, costMs(start));
            // OCR 空结果必须让索引失败，避免扫描件 PDF 以空内容写入向量库
            throw new IllegalStateException("PaddleOCR returned empty text");
        }
        log.info("PaddleOCR 调用完成：filename = {}，pages = {}，costMs = {}",
                filename, pages.size(), costMs(start));
        return pages;
    }

    /**
     * 创建 OCR HTTP client
     *
     * <p>
     * timeout 同时用于连接和读取，避免扫描 PDF 识别耗时较长时过早中断
     *
     * @param ocr OCR 配置
     * @return RestClient
     */
    private RestClient restClient(RagProperties.Ocr ocr) {
        Duration timeout = ocr.getTimeout();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .baseUrl(ocr.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 将 Resource 内容编码为 Base64
     *
     * <p>
     * 对象存储 Resource 会在这里按需读取原始 PDF 字节
     *
     * @param resource PDF 资源
     * @return Base64 字符串
     */
    private String encode(Resource resource) {
        try {
            return Base64.getEncoder().encodeToString(resource.getContentAsByteArray());
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read PDF resource for OCR", ex);
        }
    }

    /**
     * 解析 OCR 响应页面
     *
     * <p>
     * PaddleX Basic Serving 常见结构为 result.ocrResults
     * 这里同时兼容直接把 ocrResults 放在根节点的结构，降低服务端小版本差异影响
     *
     * @param response OCR 原始响应
     * @return 按页返回的 OCR 文本
     */
    private List<String> parsePages(Map<?, ?> response) {
        if (response == null || response.isEmpty()) {
            return List.of();
        }
        Object result = response.get("result");
        if (result instanceof Map<?, ?> resultMap) {
            return parseResult(resultMap);
        }
        return parseResult(response);
    }

    /**
     * 解析 OCR result 节点
     *
     * @param result result 节点或根响应
     * @return 按页返回的 OCR 文本
     */
    private List<String> parseResult(Map<?, ?> result) {
        Object ocrResults = result.get("ocrResults");
        if (ocrResults instanceof List<?> list) {
            return list.stream()
                    .map(this::parsePage)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        String singlePageText = parsePage(result);
        return StringUtils.hasText(singlePageText) ? List.of(singlePageText) : List.of();
    }

    /**
     * 解析单页 OCR 结果
     *
     * <p>
     * 优先读取 prunedResult，避免把坐标、置信度、可视化字段等非正文信息写入知识库
     *
     * @param value 单页 OCR 结果
     * @return 单页文本
     */
    private String parsePage(Object value) {
        if (value instanceof String text) {
            return text.trim();
        }
        if (!(value instanceof Map<?, ?> map)) {
            return "";
        }
        Object prunedResult = map.get("prunedResult");
        if (prunedResult instanceof Map<?, ?> prunedResultMap) {
            String text = parseTextContainer(prunedResultMap);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return parseTextContainer(map);
    }

    /**
     * 解析文本容器
     *
     * <p>
     * rec_texts 是 PaddleOCR 常见字段，recTexts 用于兼容驼峰命名适配层
     * 多行文本按换行拼接，保留 OCR 行级阅读顺序
     *
     * @param map 文本容器
     * @return 拼接后的文本
     */
    private String parseTextContainer(Map<?, ?> map) {
        Object recTexts = map.get("rec_texts");
        if (recTexts == null) {
            recTexts = map.get("recTexts");
        }
        if (recTexts instanceof List<?> list) {
            List<String> lines = new ArrayList<>();
            for (Object item : list) {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    lines.add(String.valueOf(item).trim());
                }
            }
            return String.join(System.lineSeparator(), lines);
        }
        Object text = map.get("text");
        return text == null ? "" : String.valueOf(text).trim();
    }

    private long costMs(long start) {
        return Duration.ofNanos(System.nanoTime() - start).toMillis();
    }
}
