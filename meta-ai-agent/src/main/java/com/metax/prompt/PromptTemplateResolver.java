package com.metax.prompt;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * PromptTemplateResolver .
 *
 * <p>
 * 负责按配置顺序定位并读取 prompt 模板内容
 * 外部文件位置用于 Docker 挂载覆盖，classpath 位置用于 jar 内置模板兜底
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/18
 */
@Component
public class PromptTemplateResolver {

    private static final String CLASSPATH_PREFIX = "classpath:";

    private static final String FILE_PREFIX = "file:";

    private static final String PROMPTS_PREFIX = "prompts/";

    private final PromptTemplateProperties properties;

    /**
     * 创建 prompt 模板解析器
     *
     * @param properties prompt 模板配置
     */
    public PromptTemplateResolver(PromptTemplateProperties properties) {
        this.properties = properties;
    }

    /**
     * 按 locations 顺序读取 prompt 模板内容
     *
     * <p>
     * 某个 location 中模板文件不存在时继续查找下一个 location
     * 如果 location 命中但不是普通可读文件，说明挂载或配置有误，需要快速失败
     *
     * @param templateId prompt 模板 ID
     * @return prompt 模板内容
     */
    public String resolve(PromptTemplateId templateId) {
        List<String> locations = properties.getLocations();
        if (locations == null || locations.isEmpty()) {
            throw new IllegalStateException("prompt 模板查找位置不能为空");
        }
        for (String location : locations) {
            if (!StringUtils.hasText(location)) {
                continue;
            }
            String content = resolveFromLocation(templateId, location.trim());
            if (content != null) {
                return content;
            }
        }
        throw new IllegalStateException("未找到 prompt 模板：template = " + templateId.path()
                + "，locations = " + locations);
    }

    /**
     * 从单个 location 读取模板内容
     *
     * @param templateId prompt 模板 ID
     * @param location   prompt 模板位置
     * @return prompt 模板内容，未命中时返回 null
     */
    private String resolveFromLocation(PromptTemplateId templateId, String location) {
        if (location.startsWith(FILE_PREFIX)) {
            return resolveFile(templateId, location);
        }
        if (location.startsWith(CLASSPATH_PREFIX)) {
            return resolveClasspath(templateId, location);
        }
        throw new IllegalArgumentException("不支持的 prompt 模板位置：location = " + location);
    }

    /**
     * 从文件系统 location 读取模板内容
     *
     * @param templateId prompt 模板 ID
     * @param location   文件系统 location
     * @return prompt 模板内容，未命中时返回 null
     */
    private String resolveFile(PromptTemplateId templateId, String location) {
        Path path = fileRoot(location).resolve(relativePath(templateId)).normalize();
        if (!Files.exists(path)) {
            return null;
        }
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalStateException("外部 prompt 模板不是可读取的普通文件：path = " + path);
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("读取外部 prompt 模板失败：template = " + templateId.path()
                    + "，path = " + path, ex);
        }
    }

    /**
     * 从 classpath location 读取模板内容
     *
     * @param templateId prompt 模板 ID
     * @param location   classpath location
     * @return prompt 模板内容，未命中时返回 null
     */
    private String resolveClasspath(PromptTemplateId templateId, String location) {
        String classpath = location.substring(CLASSPATH_PREFIX.length());
        String resourcePath = joinPath(classpath, relativePath(templateId));
        ClassPathResource resource = new ClassPathResource(stripLeadingSlash(resourcePath));
        if (!resource.exists()) {
            return null;
        }
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("读取 classpath prompt 模板失败：template = " + templateId.path()
                    + "，resource = " + resourcePath, ex);
        }
    }

    /**
     * 解析文件系统根目录
     *
     * @param location 文件系统 location
     * @return 文件系统根目录
     */
    private Path fileRoot(String location) {
        try {
            return Path.of(new URI(location));
        } catch (IllegalArgumentException | URISyntaxException ex) {
            throw new IllegalArgumentException("非法的 prompt 文件系统位置：location = " + location, ex);
        }
    }

    /**
     * 解析模板相对路径
     *
     * <p>
     * PromptTemplateId 使用 classpath 完整路径，locations 根目录已经表示 prompts 目录
     *
     * @param templateId prompt 模板 ID
     * @return 模板相对路径
     */
    private String relativePath(PromptTemplateId templateId) {
        return templateId.path().startsWith(PROMPTS_PREFIX)
                ? templateId.path().substring(PROMPTS_PREFIX.length())
                : templateId.path();
    }

    /**
     * 拼接资源路径
     *
     * @param root         根路径
     * @param relativePath 相对路径
     * @return 资源路径
     */
    private String joinPath(String root, String relativePath) {
        String normalizedRoot = root.endsWith("/") ? root : root + "/";
        return normalizedRoot + relativePath;
    }

    /**
     * 移除 classpath 资源前导斜杠
     *
     * @param path 资源路径
     * @return 去除前导斜杠后的资源路径
     */
    private String stripLeadingSlash(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }
}
