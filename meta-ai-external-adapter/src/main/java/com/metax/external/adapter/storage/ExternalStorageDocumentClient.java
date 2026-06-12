package com.metax.external.adapter.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.metax.external.adapter.config.ExternalAdapterProperties;
import com.metax.external.adapter.source.ExternalDownloadedFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * ExternalStorageDocumentClient .
 *
 * <p>
 * 通过现有对象存储文档 HTTP API 接入 StorageDocument 和 ETL 链路
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/11
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "metax.external-adapter.enabled", havingValue = "true")
public class ExternalStorageDocumentClient {

    private final WebClient externalStorageWebClient;

    private final ExternalAdapterProperties properties;

    /**
     * 上传文件并按配置自动提交索引
     *
     * @param file 外部下载文件
     * @return 上传结果
     */
    public ExternalStorageUploadResult upload(ExternalDownloadedFile file) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("tenantId", properties.getTenantId());
        builder.part("kbId", properties.getKbId());
        builder.part("visibility", "PUBLIC");
        builder.part("autoIndex", String.valueOf(properties.getStorageApi().isAutoIndex()));
        builder.part("file", resource(file))
                .filename(file.filename())
                .contentType(MediaType.parseMediaType(file.contentType()));

        JsonNode response = externalStorageWebClient.post()
                .uri("/v1/storage/documents/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(properties.getStorageApi().getUploadTimeout());
        if (response == null || response.path("code").asInt() != 200) {
            throw new IllegalStateException("对象存储文档上传失败：" + response);
        }
        JsonNode data = response.path("data");
        String documentId = data.path("documentId").asText();
        if (!StringUtils.hasText(documentId)) {
            throw new IllegalStateException("对象存储文档上传响应缺少 documentId");
        }
        String indexStatus = data.path("indexStatus").asText();
        log.info("第三方系统文件已上传到对象存储文档：documentId = {}，indexStatus = {}", documentId, indexStatus);
        return new ExternalStorageUploadResult(documentId, indexStatus);
    }

    /**
     * 重新提交对象存储文档索引
     *
     * <p>
     * 用于文件已成功进入对象存储但 ETL / OCR 失败后的重试场景
     * 避免重复从第三方系统下载并重复写入对象存储
     *
     * @param documentId 本系统文档 ID
     */
    public void index(String documentId) {
        JsonNode response = externalStorageWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/storage/documents/{documentId}/index")
                        .queryParam("tenantId", properties.getTenantId())
                        .queryParam("kbId", properties.getKbId())
                        .build(documentId))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(properties.getStorageApi().getUploadTimeout());
        if (response == null || response.path("code").asInt() != 200) {
            throw new IllegalStateException("对象存储文档索引提交失败：" + response);
        }
        log.info("第三方系统文件已重新提交对象存储文档索引：documentId = {}", documentId);
    }

    /**
     * 创建带文件名的字节资源
     *
     * @param file 外部下载文件
     * @return 字节资源
     */
    private ByteArrayResource resource(ExternalDownloadedFile file) {
        return new ByteArrayResource(file.bytes()) {
            @Override
            public String getFilename() {
                return file.filename();
            }
        };
    }
}
