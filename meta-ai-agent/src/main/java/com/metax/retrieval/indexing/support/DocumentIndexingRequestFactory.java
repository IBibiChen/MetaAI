package com.metax.retrieval.indexing.support;

import com.metax.chat.support.ChatDefaults;
import com.metax.rag.etl.model.DocumentSourceType;
import com.metax.rag.indexing.DocumentIndexingRequest;
import com.metax.retrieval.indexing.request.DocumentImportRequest;
import org.springframework.stereotype.Component;

/**
 * DocumentIndexingRequestFactory .
 *
 * <p>
 * 文档索引导入请求转换工厂
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@Component
public class DocumentIndexingRequestFactory {

    /**
     * 转换对象存储文档索引导入请求
     *
     * @param request 对象存储文档索引导入请求参数
     * @return 文档索引请求
     */
    public DocumentIndexingRequest objectStorageRequest(DocumentImportRequest request) {
        // 接口层只接收对象存储定位信息，Reader 和 Transformer 链路由核心索引请求统一驱动
        return DocumentIndexingRequest.builder()
                .tenantId(request.getTenantId())
                .kbId(request.getKbId())
                .documentId(request.getDocumentId())
                .visibility(valueOrDefault(request.getVisibility(), ChatDefaults.VISIBILITY))
                .deptId(request.getDeptId())
                .userId(request.getUserId())
                .documentType(request.getDocumentType())
                .sourceType(DocumentSourceType.OBJECT_STORAGE)
                .source(request.getSource())
                // filename 只用于展示和排查，真实读取仍然依赖 bucket + objectKey
                .filename(filenameFromPath(request.getObjectKey()))
                .bucket(request.getBucket())
                .objectKey(request.getObjectKey())
                .build();
    }

    /**
     * 解析文档可见性
     *
     * @param visibility 原始文档可见性
     * @return 兜底后的文档可见性
     */
    public String visibility(String visibility) {
        // 对外保留独立方法，方便旧存储接口和索引导入接口共用同一套默认可见性
        return valueOrDefault(visibility, ChatDefaults.VISIBILITY);
    }

    /**
     * 解析默认值
     *
     * @param value        原始值
     * @param defaultValue 默认值
     * @return 兜底后的值
     */
    private String valueOrDefault(String value, String defaultValue) {
        // visibility 为空时默认 PUBLIC，保持导入接口和旧存储接口的默认行为一致
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /**
     * 从对象路径提取展示文件名
     *
     * @param path 对象存储路径
     * @return 文件名
     */
    private String filenameFromPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        // objectKey 可能包含 Windows 或 S3 风格路径分隔符，统一转成 slash 后提取文件名
        String normalized = path.replace("\\", "/");
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }
}
