package com.metax.external.adapter.source;

import com.metax.external.adapter.config.ExternalAdapterProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

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
}
