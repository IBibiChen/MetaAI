package com.metax.external.adapter.source;

import cn.hutool.core.io.FileTypeUtil;
import com.metax.external.adapter.config.ExternalAdapterProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.OptionalLong;

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
        DataSize maxFileSize = properties.getFileService().getMaxFileSize();
        DataSize maxInMemorySize = properties.getFileService().getMaxInMemorySize();
        log.info("开始下载第三方系统文件：externalFileId = {}，filePath = {}，requestUrl = {}，authorization = {}，timeout = {}，maxFileSizeMB = {}，maxInMemorySizeMB = {}",
                file.getId(), file.getFilePath(), requestUrl, authorization, properties.getFileService().getTimeout(),
                maxFileSize.toMegabytes(), maxInMemorySize.toMegabytes());
        DownloadResponse response = downloadBytes(requestUri, authorization, file, maxFileSize, maxInMemorySize);
        byte[] bytes = response.bytes();
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("外部文件内容为空");
        }
        validateFileSize(file, bytes.length, maxFileSize);
        String filename = resolveFilename(file);
        String contentType = resolveContentType(filename, response.contentType(), bytes);
        log.info("第三方系统文件下载完成：externalFileId = {}，filename = {}，fileSize = {}，fileSizeMB = {}，contentType = {}",
                file.getId(), filename, bytes.length, megabytes(bytes.length), contentType);
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
     * @param filename            文件名
     * @param responseContentType 响应内容类型
     * @param bytes               文件字节
     * @return 内容类型
     */
    private String resolveContentType(String filename, String responseContentType, byte[] bytes) {
        if (StringUtils.hasText(responseContentType)) {
            return responseContentType;
        }
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

    /**
     * 下载外部文件字节内容
     *
     * <p>
     * 当前部署按单机串行学习设计，允许单个文件进入内存，但必须通过 maxFileSize 和 maxInMemorySize 双重限制兜住风险
     *
     * @param requestUri      请求 URI
     * @param authorization   Authorization Header
     * @param file            第三方系统文件快照
     * @param maxFileSize     外部文件最大学习大小
     * @param maxInMemorySize WebClient 单文件内存缓冲上限
     * @return 下载响应
     */
    private DownloadResponse downloadBytes(URI requestUri,
                                           String authorization,
                                           ExternalSourceFileDO file,
                                           DataSize maxFileSize,
                                           DataSize maxInMemorySize) {
        try {
            return externalFileWebClient.get()
                    .uri(requestUri)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .exchangeToMono(response -> readResponse(response, file, maxFileSize))
                    .block(properties.getFileService().getTimeout());
        } catch (RuntimeException ex) {
            if (containsDataBufferLimitException(ex)) {
                throw new IllegalStateException("外部文件超过下载内存缓冲上限：externalFileId = %s，maxInMemorySizeMB = %d"
                        .formatted(file.getId(), maxInMemorySize.toMegabytes()), ex);
            }
            throw ex;
        }
    }

    /**
     * 读取外部文件下载响应
     *
     * <p>
     * 响应头如果带 Content-Length，优先在读取正文前完成大小校验，避免明显超限文件进入内存缓冲
     *
     * @param response    下载响应
     * @param file        第三方系统文件快照
     * @param maxFileSize 外部文件最大学习大小
     * @return 下载响应
     */
    private Mono<DownloadResponse> readResponse(ClientResponse response,
                                                ExternalSourceFileDO file,
                                                DataSize maxFileSize) {
        if (response.statusCode().isError()) {
            return response.createException().flatMap(Mono::error);
        }
        OptionalLong contentLength = response.headers().contentLength();
        contentLength.ifPresent(length -> validateFileSize(file, length, maxFileSize));
        String contentType = response.headers().contentType()
                .map(MediaType::toString)
                .orElse(null);
        return response.bodyToMono(byte[].class)
                .map(bytes -> new DownloadResponse(contentType, bytes));
    }

    /**
     * 校验外部文件大小
     *
     * @param file        第三方系统文件快照
     * @param fileSize    文件大小
     * @param maxFileSize 外部文件最大学习大小
     */
    private void validateFileSize(ExternalSourceFileDO file, long fileSize, DataSize maxFileSize) {
        long maxFileSizeBytes = maxFileSize.toBytes();
        if (fileSize > maxFileSizeBytes) {
            throw new IllegalStateException("外部文件超过最大学习大小：externalFileId = %s，fileSize = %d，fileSizeMB = %s，maxFileSizeMB = %d"
                    .formatted(file.getId(), fileSize, megabytes(fileSize), maxFileSize.toMegabytes()));
        }
    }

    /**
     * 将字节大小转换为 MB 展示值
     *
     * @param bytes 字节数
     * @return 保留两位小数的 MB 字符串
     */
    private String megabytes(long bytes) {
        return BigDecimal.valueOf(bytes)
                .divide(BigDecimal.valueOf(1024L * 1024L), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    /**
     * 判断异常链中是否包含 DataBufferLimitException
     *
     * @param ex 原始异常
     * @return 是否包含 DataBufferLimitException
     */
    private boolean containsDataBufferLimitException(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof DataBufferLimitException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record DownloadResponse(String contentType, byte[] bytes) {

    }
}
