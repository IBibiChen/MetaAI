package com.metax.rag.etl.reader;

import com.metax.rag.config.MetaRetrievalProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PaddleOcrClientTest .
 *
 * <p>
 * PaddleOCR HTTP 客户端单元测试
 * 使用 JDK HttpServer 模拟本地 OCR 服务，避免单元测试依赖真实 Docker 容器
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/5
 */
class PaddleOcrClientTest {

    private HttpServer server;

    private final AtomicReference<String> requestBody = new AtomicReference<>();

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * 应能解析 PaddleX OCR PDF 多页响应
     *
     * <p>
     * 每个 ocrResults 元素代表一页，prunedResult.rec_texts 是该页按行识别出的正文
     */
    @Test
    void shouldParsePaddleOcrPdfPages() throws Exception {
        server = server("""
                {
                  "result": {
                    "ocrResults": [
                      {"prunedResult": {"rec_texts": ["第一页第一行", "第一页第二行"]}},
                      {"prunedResult": {"rec_texts": ["第二页"]}}
                    ]
                  }
                }
                """);
        PaddleOcrClient client = new PaddleOcrClient(properties(server));

        List<String> pages = client.recognize(resource(), "pdf");

        assertThat(pages).containsExactly("第一页第一行" + System.lineSeparator() + "第一页第二行", "第二页");
        assertThat(requestBody.get()).contains("\"fileType\":0");
    }

    /**
     * 图片 OCR 应使用 PaddleX image fileType
     *
     * <p>
     * PaddleX /ocr 接口使用 fileType 区分 PDF 和图片，图片必须传 1
     */
    @Test
    void shouldUseImageFileTypeForImageOcr() throws Exception {
        server = server("""
                {
                  "result": {
                    "ocrResults": [
                      {"prunedResult": {"rec_texts": ["图片 OCR 文本"]}}
                    ]
                  }
                }
                """);
        PaddleOcrClient client = new PaddleOcrClient(properties(server));

        List<String> pages = client.recognize(resource(), "jpg");

        assertThat(pages).containsExactly("图片 OCR 文本");
        assertThat(requestBody.get()).contains("\"fileType\":1");
    }

    /**
     * OCR 空文本必须失败
     *
     * <p>
     * 扫描 PDF 或图片如果识别不到正文，继续入库只会产生不可检索的空向量数据
     */
    @Test
    void shouldFailWhenPaddleOcrReturnsEmptyText() throws Exception {
        server = server("""
                {
                  "result": {
                    "ocrResults": [
                      {"prunedResult": {"rec_texts": []}}
                    ]
                  }
                }
                """);
        PaddleOcrClient client = new PaddleOcrClient(properties(server));

        assertThatThrownBy(() -> client.recognize(resource(), "pdf"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PaddleOCR returned empty text");
    }

    /**
     * 不支持的 documentType 不应调用 OCR 服务
     */
    @Test
    void shouldRejectUnsupportedDocumentType() throws Exception {
        server = server("{}");
        PaddleOcrClient client = new PaddleOcrClient(properties(server));

        assertThatThrownBy(() -> client.recognize(resource(), "txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported OCR documentType");
    }

    /**
     * 启动测试用 OCR HTTP 服务
     *
     * @param response OCR 响应 JSON
     * @return HttpServer
     */
    private HttpServer server(String response) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/ocr", exchange -> {
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        httpServer.start();
        return httpServer;
    }

    /**
     * 创建测试 OCR 配置
     *
     * @param httpServer 测试 HTTP 服务
     * @return MetaRetrievalProperties
     */
    private MetaRetrievalProperties properties(HttpServer httpServer) {
        MetaRetrievalProperties properties = new MetaRetrievalProperties();
        properties.getOcr().setBaseUrl("http://localhost:" + httpServer.getAddress().getPort());
        properties.getOcr().setTimeout(Duration.ofSeconds(5));
        return properties;
    }

    /**
     * 构造测试 PDF 资源
     *
     * @return ByteArrayResource
     */
    private ByteArrayResource resource() {
        return new ByteArrayResource("pdf".getBytes(StandardCharsets.UTF_8));
    }
}
