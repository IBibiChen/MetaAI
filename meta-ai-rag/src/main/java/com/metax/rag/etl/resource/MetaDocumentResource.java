package com.metax.rag.etl.resource;

import org.springframework.core.io.Resource;

/**
 * MetaDocumentResource .
 *
 * <p>
 * MetaAI 文档资源描述，把不同来源的文件统一抽象为 Spring Resource
 * MetaDocumentReader 只关心 Resource 和最终 documentType，不关心文件来自对象存储还是本地目录
 *
 * @param resource     Spring Resource
 * @param documentType 最终文档类型
 * @param source       引用来源
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
public record MetaDocumentResource(
        Resource resource,
        String documentType,
        String source
) {
}

