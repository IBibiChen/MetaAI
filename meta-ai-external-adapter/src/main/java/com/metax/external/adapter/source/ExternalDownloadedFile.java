package com.metax.external.adapter.source;

/**
 * ExternalDownloadedFile .
 *
 * <p>
 * 外部文件服务 下载文件内容
 *
 * @param filename    文件名
 * @param contentType 内容类型
 * @param bytes       文件字节
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/11
 */
public record ExternalDownloadedFile(
        String filename,
        String contentType,
        byte[] bytes
) {
}
