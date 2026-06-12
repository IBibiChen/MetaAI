package com.metax.external.adapter.web;

import lombok.RequiredArgsConstructor;
import com.metax.external.adapter.sync.ExternalDocumentSyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ExternalDocumentSyncController .
 *
 * <p>
 * 第三方系统文件学习通知接口
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/11
 */
@Validated
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "metax.external-adapter.enabled", havingValue = "true")
public class ExternalDocumentSyncController {

    private final ExternalDocumentSyncService syncService;

    /**
     * 批量接收第三方系统文件学习通知
     *
     * <p>
     * 第三方系统上传并保存文件元数据后统一调用该接口，只有 1 个文件时也传单元素列表
     * 服务端仍然逐条写入持久队列
     * 真正下载、上传和索引由后台 Worker 串行消费
     *
     * @param request 批量学习通知请求
     * @return 同步状态列表
     */
    @PostMapping("/internal/external/documents/sync")
    public List<ExternalDocumentSyncResponse> syncBatch(@Validated @RequestBody ExternalDocumentBatchSyncRequest request) {
        return request.externalFileIds().stream()
                .filter(externalFileId -> externalFileId != null && !externalFileId.isBlank())
                .map(syncService::requestSync)
                .toList();
    }
}
