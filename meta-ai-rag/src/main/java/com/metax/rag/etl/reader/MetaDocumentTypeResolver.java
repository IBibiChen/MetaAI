package com.metax.rag.etl.reader;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

/**
 * MetaDocumentTypeResolver .
 *
 * <p>
 * 文档类型解析器，优先使用调用方显式传入的 documentType，未传入时根据文件名或 objectKey 后缀推断
 * 无法识别时返回 tika，由 TikaDocumentReader 兜底解析
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
@Component
public class MetaDocumentTypeResolver {

    private static final String TIKA = "tika";

    private static final Map<String, String> EXTENSIONS = Map.ofEntries(
            Map.entry("txt", "txt"),
            Map.entry("md", "markdown"),
            Map.entry("markdown", "markdown"),
            Map.entry("json", "json"),
            Map.entry("pdf", "pdf"),
            Map.entry("doc", "doc"),
            Map.entry("docx", "docx"),
            Map.entry("ppt", "ppt"),
            Map.entry("pptx", "pptx"),
            Map.entry("html", "html"),
            Map.entry("htm", "html")
    );

    /**
     * 解析文档类型
     *
     * @param explicitType 显式文档类型
     * @param filename     文件名或 objectKey
     * @return 标准文档类型
     */
    public String resolve(String explicitType, String filename) {
        if (StringUtils.hasText(explicitType)) {
            return normalize(explicitType);
        }
        String extension = extension(filename);
        return EXTENSIONS.getOrDefault(extension, TIKA);
    }

    private String normalize(String value) {
        String type = value.toLowerCase(Locale.ROOT).trim().replace(".", "");
        return EXTENSIONS.getOrDefault(type, type);
    }

    private String extension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        String normalized = filename.toLowerCase(Locale.ROOT);
        int index = normalized.lastIndexOf('.');
        return index < 0 || index == normalized.length() - 1 ? "" : normalized.substring(index + 1);
    }
}

