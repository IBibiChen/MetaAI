package com.metax.prompt;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.template.TemplateRenderer;
import org.springframework.ai.template.ValidationMode;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * PromptTemplates .
 *
 * <p>
 * prompt 模板渲染工具类，适用于配置类、测试和简单调用场景
 * Spring Bean 入口由 PromptTemplateResolver 负责资源定位，当前类只保留模板渲染和 classpath 默认兜底能力
 *
 * <p>
 * 当前类不是动态 prompt 的最终形态
 * 后续如果引入数据库 prompt、租户 prompt、灰度 prompt 或热更新 prompt，应优先通过 PromptTemplateService 演进
 * PromptTemplateService 当前保留为 Spring Bean，负责承接未来动态 prompt 能力
 *
 * <p>
 * Spring AI 1.1.7 的 PromptTemplate(Resource) 使用 Charset.defaultCharset 读取资源
 * Windows 环境可能不是 UTF-8，因此当前工具统一显式按 UTF-8 读取模板内容
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
public final class PromptTemplates {

    private static final TemplateRenderer ANGLE_BRACKET_RENDERER = StTemplateRenderer.builder()
            .startDelimiterToken('<')
            .endDelimiterToken('>')
            .validationMode(ValidationMode.THROW)
            .build();

    private PromptTemplates() {
    }

    /**
     * 渲染 prompt 纯文本
     *
     * @param templateId prompt 模板 ID
     * @return 渲染后的 prompt 文本
     */
    public static String render(PromptTemplateId templateId) {
        return render(PromptTemplateRequest.of(templateId));
    }

    /**
     * 渲染 prompt 纯文本
     *
     * @param request prompt 模板请求
     * @return 渲染后的 prompt 文本
     */
    public static String render(PromptTemplateRequest request) {
        validateVariables(request.templateId(), request.variables());
        return createPromptTemplate(request.templateId()).render(request.variables());
    }

    /**
     * 创建 system prompt Message
     *
     * @param request prompt 模板请求
     * @return system message
     */
    public static Message createSystemMessage(PromptTemplateRequest request) {
        validateVariables(request.templateId(), request.variables());
        return createSystemPromptTemplate(request.templateId()).createMessage(request.variables());
    }

    /**
     * 创建 Spring AI Prompt
     *
     * @param request prompt 模板请求
     * @return Spring AI Prompt
     */
    public static Prompt create(PromptTemplateRequest request) {
        validateVariables(request.templateId(), request.variables());
        PromptTemplate template = createPromptTemplate(request.templateId());
        if (request.chatOptions() == null) {
            return template.create(request.variables());
        }
        return template.create(request.variables(), request.chatOptions());
    }

    /**
     * 使用尖括号分隔符渲染 prompt 纯文本
     *
     * @param request prompt 模板请求
     * @return 渲染后的 prompt 文本
     */
    public static String renderWithAngleBrackets(PromptTemplateRequest request) {
        validateVariables(request.templateId(), request.variables());
        return PromptTemplate.builder()
                .template(templateContent(request.templateId()))
                .variables(request.variables())
                .renderer(ANGLE_BRACKET_RENDERER)
                .build()
                .render();
    }

    /**
     * 创建普通 PromptTemplate
     *
     * @param templateId prompt 模板 ID
     * @return PromptTemplate
     */
    private static PromptTemplate createPromptTemplate(PromptTemplateId templateId) {
        return new PromptTemplate(templateContent(templateId));
    }

    /**
     * 创建 SystemPromptTemplate
     *
     * @param templateId prompt 模板 ID
     * @return SystemPromptTemplate
     */
    private static SystemPromptTemplate createSystemPromptTemplate(PromptTemplateId templateId) {
        return new SystemPromptTemplate(templateContent(templateId));
    }

    /**
     * 加载 classpath prompt 资源
     *
     * @param templateId prompt 模板 ID
     * @return classpath 资源
     */
    private static Resource resource(PromptTemplateId templateId) {
        return new ClassPathResource(templateId.path());
    }

    /**
     * 读取 UTF-8 prompt 模板内容
     *
     * @param templateId prompt 模板 ID
     * @return prompt 模板内容
     */
    private static String templateContent(PromptTemplateId templateId) {
        try {
            return resource(templateId).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read prompt template: " + templateId.path(), ex);
        }
    }

    /**
     * 渲染指定模板内容
     *
     * @param request         prompt 模板请求
     * @param templateContent prompt 模板内容
     * @return 渲染后的 prompt 文本
     */
    public static String render(PromptTemplateRequest request, String templateContent) {
        validateVariables(request.templateId(), request.variables());
        return new PromptTemplate(templateContent).render(request.variables());
    }

    /**
     * 基于指定模板内容创建 system prompt Message
     *
     * @param request         prompt 模板请求
     * @param templateContent prompt 模板内容
     * @return system message
     */
    public static Message createSystemMessage(PromptTemplateRequest request, String templateContent) {
        validateVariables(request.templateId(), request.variables());
        return new SystemPromptTemplate(templateContent).createMessage(request.variables());
    }

    /**
     * 基于指定模板内容创建 Spring AI Prompt
     *
     * @param request         prompt 模板请求
     * @param templateContent prompt 模板内容
     * @return Spring AI Prompt
     */
    public static Prompt create(PromptTemplateRequest request, String templateContent) {
        validateVariables(request.templateId(), request.variables());
        PromptTemplate template = new PromptTemplate(templateContent);
        if (request.chatOptions() == null) {
            return template.create(request.variables());
        }
        return template.create(request.variables(), request.chatOptions());
    }

    /**
     * 使用尖括号分隔符渲染指定模板内容
     *
     * @param request         prompt 模板请求
     * @param templateContent prompt 模板内容
     * @return 渲染后的 prompt 文本
     */
    public static String renderWithAngleBrackets(PromptTemplateRequest request, String templateContent) {
        validateVariables(request.templateId(), request.variables());
        return PromptTemplate.builder()
                .template(templateContent)
                .variables(request.variables())
                .renderer(ANGLE_BRACKET_RENDERER)
                .build()
                .render();
    }

    /**
     * 校验模板必填变量
     *
     * @param templateId prompt 模板 ID
     * @param variables  模板变量
     */
    private static void validateVariables(PromptTemplateId templateId, Map<String, Object> variables) {
        Set<String> missingVariables = templateId.requiredVariables()
                .stream()
                .filter(variable -> !variables.containsKey(variable))
                .collect(java.util.stream.Collectors.toSet());
        if (!missingVariables.isEmpty()) {
            throw new IllegalArgumentException("Missing prompt variables: " + missingVariables);
        }
    }

}
