package com.metax.external.adapter.source;

import cn.hutool.core.io.FileTypeUtil;
import com.metax.external.adapter.config.ExternalAdapterProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayInputStream;
import java.net.URI;

/**
 * ExternalFileDownloadClient .
 *
 * <p>
 * 第三方系统文件下载客户端
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/11
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "metax.external-adapter.enabled", havingValue = "true")
public class ExternalFileDownloadClient {

    private final WebClient externalFileWebClient;

    private final ExternalAdapterProperties properties;

    /**
     * 下载第三方系统文件
     *
     * @param file 第三方系统文件快照
     * @return 外部文件内容
     */
    public ExternalDownloadedFile download(ExternalSourceFileDO file) {
        String path = normalizePath(file.getFilePath());
        String url = properties.getFileService().getDownloadUrl() + path;
        String requestUrl = buildRequestUrl(url);
        URI requestUri = URI.create(requestUrl);
        String authorization = properties.getFileService().getAuthorization();
        log.info("开始下载第三方系统文件：externalFileId = {}，filePath = {}，requestUrl = {}，authorization = {}，timeout = {}",
                file.getId(), file.getFilePath(), requestUrl, authorization, properties.getFileService().getTimeout());
        byte[] bytes = externalFileWebClient.get()
                .uri(requestUri)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .retrieve()
                .bodyToMono(byte[].class)
                .block(properties.getFileService().getTimeout());
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("外部文件内容为空");
        }
        String filename = resolveFilename(file);
        String contentType = resolveContentType(filename, bytes);
        log.info("第三方系统文件下载完成：externalFileId = {}，filename = {}，fileSize = {}，contentType = {}",
                file.getId(), filename, bytes.length, contentType);
        return new ExternalDownloadedFile(filename, contentType, bytes);
    }

    /**
     * 构造下载请求完整 URL
     *
     * <p>
     * 下载请求使用完整 URI 发起，避免 WebClient 对已编码的 filePath 再次编码
     * 这里仍复用 file-service.host 和 download-url 的拼接规则，保持配置语义不变
     *
     * @param url 下载相对路径
     * @return 下载请求完整 URL
     */
    private String buildRequestUrl(String url) {
        String host = properties.getFileService().getHost();
        if (!StringUtils.hasText(host)) {
            return url;
        }
        if (host.endsWith("/") && url.startsWith("/")) {
            return host.substring(0, host.length() - 1) + url;
        }
        if (!host.endsWith("/") && !url.startsWith("/")) {
            return host + "/" + url;
        }
        return host + url;
    }

    /**
     * 规范化外部文件路径
     *
     * <p>
     * 老系统上传 FSIP 时已经对文件名做了 URL 编码，数据库中的 filePath 是可直接拼接到下载 URL 的路径
     * 这里不能再次 URLEncoder.encode，否则 %E4 会被重复编码为 %25E4，文件服务会返回 400
     *
     * @param filePath 第三方系统文件路径
     * @return 可直接拼接到下载 URL 的相对路径
     */
    private String normalizePath(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            throw new IllegalArgumentException("外部文件路径不能为空");
        }
        return filePath.startsWith("/") ? filePath.substring(1) : filePath;
    }

    /**
     * 解析文件名
     *
     * @param file 第三方系统文件快照
     * @return 文件名
     */
    private String resolveFilename(ExternalSourceFileDO file) {
        if (StringUtils.hasText(file.getFileName())) {
            return file.getFileName();
        }
        String filePath = file.getFilePath();
        int index = filePath == null ? -1 : filePath.lastIndexOf('/');
        return index < 0 ? "external-file" : filePath.substring(index + 1);
    }

    /**
     * 解析内容类型
     *
     * @param filename 文件名
     * @param bytes    文件字节
     * @return 内容类型
     */
    private String resolveContentType(String filename, byte[] bytes) {
        return MediaTypeFactory.getMediaType(filename)
                .map(MediaType::toString)
                .orElseGet(() -> resolveContentTypeByBytes(bytes));
    }

    /**
     * 根据文件内容解析内容类型
     *
     * @param bytes 文件字节
     * @return 内容类型
     */
    private String resolveContentTypeByBytes(byte[] bytes) {
        String type = FileTypeUtil.getType(new ByteArrayInputStream(bytes));
        return StringUtils.hasText(type) ? "application/" + type : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
