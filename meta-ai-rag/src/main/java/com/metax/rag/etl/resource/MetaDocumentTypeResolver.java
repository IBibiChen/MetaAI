package com.metax.rag.etl.resource;

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

    /**
     * 文件后缀到内部文档类型的转换表
     *
     * <p>
     * 内部文档类型会直接参与 Reader 策略选择，例如 md 归一为 markdown，htm 归一为 html
     * xls / xlsx 保留为独立类型，确保 Excel 文件命中 ExcelDocumentReaderStrategy
     */
    private static final Map<String, String> EXTENSIONS = Map.ofEntries(
            Map.entry("txt", "txt"),
            Map.entry("md", "markdown"),
            Map.entry("markdown", "markdown"),
            Map.entry("json", "json"),
            Map.entry("pdf", "pdf"),
            Map.entry("png", "png"),
            Map.entry("jpg", "jpg"),
            Map.entry("jpeg", "jpeg"),
            Map.entry("webp", "webp"),
            Map.entry("bmp", "bmp"),
            Map.entry("tif", "tif"),
            Map.entry("tiff", "tiff"),
            Map.entry("doc", "doc"),
            Map.entry("docx", "docx"),
            Map.entry("xls", "xls"),
            Map.entry("xlsx", "xlsx"),
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
            // 调用方显式传入的类型优先，避免对象存储 key 或下载文件名后缀误导 Reader 选择
            return normalize(explicitType);
        }
        // 未显式指定时只根据文件名后缀做轻量推断，无法识别则交给 Tika 兜底
        String extension = extension(filename);
        return EXTENSIONS.getOrDefault(extension, TIKA);
    }

    /**
     * 标准化显式文档类型
     *
     * <p>
     * 支持调用方传入 .docx、DOCX 或 markdown 这类大小写和点号不一致的值
     * 未知类型原样返回，允许后续扩展策略自行识别
     *
     * @param value 显式文档类型
     * @return 标准文档类型
     */
    private String normalize(String value) {
        String type = value.toLowerCase(Locale.ROOT).trim().replace(".", "");
        return EXTENSIONS.getOrDefault(type, type);
    }

    /**
     * 提取文件名后缀
     *
     * <p>
     * 空文件名、无后缀文件名和以点号结尾的文件名都返回空字符串
     *
     * @param filename 文件名或 objectKey
     * @return 小写后缀，不包含点号
     */
    private String extension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        String normalized = filename.toLowerCase(Locale.ROOT);
        int index = normalized.lastIndexOf('.');
        // index 位于末尾表示文件名以点号结尾，这类输入不能当作有效后缀
        return index < 0 || index == normalized.length() - 1 ? "" : normalized.substring(index + 1);
    }
}
