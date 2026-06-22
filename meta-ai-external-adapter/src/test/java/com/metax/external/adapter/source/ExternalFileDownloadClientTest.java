package com.metax.external.adapter.source;

import com.metax.external.adapter.config.ExternalAdapterProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.util.unit.DataSize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ExternalFileDownloadClientTest .
 */
class ExternalFileDownloadClientTest {

    @Test
    void downloadShouldPreserveAlreadyEncodedFilePath() {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://loki:10086")
                .exchangeFunction(request -> {
                    requestedUri.set(request.url());
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                            .body("demo")
                            .build());
                })
                .build();
        ExternalAdapterProperties properties = new ExternalAdapterProperties();
        properties.getFileService().setHost("http://loki:10086");
        properties.getFileService().setDownloadUrl("/v1/download/");
        properties.getFileService().setAuthorization("123010068");
        ExternalFileDownloadClient client = new ExternalFileDownloadClient(webClient, properties);

        ExternalSourceFileDO file = new ExternalSourceFileDO();
        file.setId("file-1");
        file.setFileName("乐山市自然环境问题清单整改报告.docx");
        file.setFilePath("disk/123010068/20260622/%E4%B9%90%E5%B1%B1%E5%B8%82.docx");

        ExternalDownloadedFile downloadedFile = client.download(file);

        assertThat(new String(downloadedFile.bytes(), StandardCharsets.UTF_8)).isEqualTo("demo");
        assertThat(requestedUri.get().toString())
                .isEqualTo("http://loki:10086/v1/download/disk/123010068/20260622/%E4%B9%90%E5%B1%B1%E5%B8%82.docx");
        assertThat(requestedUri.get().toString()).doesNotContain("%25E4");
    }

    @Test
    void downloadShouldAllowLargeFileWithinConfiguredMemoryLimit() {
        byte[] body = bytes(300 * 1024);
        WebClient webClient = webClient(body, DataSize.ofMegabytes(1), body.length);
        ExternalAdapterProperties properties = properties();
        properties.getFileService().setMaxInMemorySize(DataSize.ofMegabytes(1));
        properties.getFileService().setMaxFileSize(DataSize.ofMegabytes(1));
        ExternalFileDownloadClient client = new ExternalFileDownloadClient(webClient, properties);

        ExternalDownloadedFile downloadedFile = client.download(file("large.pdf"));

        assertThat(downloadedFile.bytes()).hasSize(body.length);
        assertThat(downloadedFile.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void downloadShouldRejectFileExceedingBusinessSizeLimitByContentLength() {
        byte[] body = bytes(16);
        WebClient webClient = webClient(body, DataSize.ofMegabytes(1), 2 * 1024 * 1024L);
        ExternalAdapterProperties properties = properties();
        properties.getFileService().setMaxInMemorySize(DataSize.ofMegabytes(1));
        properties.getFileService().setMaxFileSize(DataSize.ofMegabytes(1));
        ExternalFileDownloadClient client = new ExternalFileDownloadClient(webClient, properties);

        assertThatThrownBy(() -> client.download(file("too-large.pdf")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("外部文件超过最大学习大小")
                .hasMessageContaining("fileSizeMB")
                .hasMessageContaining("maxFileSizeMB");
    }

    @Test
    void downloadShouldConvertBufferLimitExceptionToBusinessMessage() {
        byte[] body = bytes(300 * 1024);
        WebClient webClient = webClient(body, DataSize.ofKilobytes(128), body.length);
        ExternalAdapterProperties properties = properties();
        properties.getFileService().setMaxInMemorySize(DataSize.ofKilobytes(128));
        properties.getFileService().setMaxFileSize(DataSize.ofMegabytes(1));
        ExternalFileDownloadClient client = new ExternalFileDownloadClient(webClient, properties);

        assertThatThrownBy(() -> client.download(file("buffer-limit.pdf")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("外部文件超过下载内存缓冲上限")
                .hasMessageContaining("maxInMemorySizeMB");
    }

    @Test
    void downloadShouldRejectEmptyFile() {
        WebClient webClient = webClient(new byte[0], DataSize.ofMegabytes(1), 0L);
        ExternalFileDownloadClient client = new ExternalFileDownloadClient(webClient, properties());

        assertThatThrownBy(() -> client.download(file("empty.pdf")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("外部文件内容为空");
    }

    private WebClient webClient(byte[] body, DataSize maxInMemorySize, long contentLength) {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(
                        Math.toIntExact(maxInMemorySize.toBytes())))
                .build();
        return WebClient.builder()
                .exchangeStrategies(strategies)
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK, strategies)
                        .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
                        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
                        .body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(body)))
                        .build()))
                .build();
    }

    private ExternalAdapterProperties properties() {
        ExternalAdapterProperties properties = new ExternalAdapterProperties();
        properties.getFileService().setHost("http://loki:10086");
        properties.getFileService().setDownloadUrl("/v1/download/");
        properties.getFileService().setAuthorization("123010068");
        return properties;
    }

    private ExternalSourceFileDO file(String filename) {
        ExternalSourceFileDO file = new ExternalSourceFileDO();
        file.setId("file-1");
        file.setFileName(filename);
        file.setFilePath("disk/123010068/20260622/" + filename);
        return file;
    }

    private byte[] bytes(int size) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) 'a');
        return bytes;
    }
}
