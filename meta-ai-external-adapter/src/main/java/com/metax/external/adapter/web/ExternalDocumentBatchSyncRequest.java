package com.metax.external.adapter.web;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * ExternalDocumentBatchSyncRequest .
 *
 * <p>
 * 第三方系统文件批量学习通知请求
 *
 * @param externalFileIds 第三方系统文件 ID 列表
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/12
 */
public record ExternalDocumentBatchSyncRequest(
        @NotEmpty(message = "externalFileIds 不能为空")
        List<String> externalFileIds
) {
}
