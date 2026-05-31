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
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * PromptTemplateService .
 *
 * <p>
 * 统一封装 Spring AI PromptTemplate / SystemPromptTemplate 的加载和渲染
 * 当前项目采用 classpath prompt 资源 + Git 版本管理，不把大段 prompt 文本写死在配置类或业务类中
 *
 * <p>
 * Prompt 模块只负责模板加载、变量校验和渲染
 * 模型 provider、ChatMemory、VectorStore、Advisor 顺序仍由 ChatClient 配置层负责
 * 业务代码只引用 PromptTemplateId，不直接引用 prompt 文件路径
 * prompt 文件路径只在 PromptTemplateId 中维护，新增模板必须先确定 scene、purpose、role
 *
 * <p>
 * Spring AI Prompt 学习笔记
 * Prompt 是一次模型请求的载体，内部包含 Message 列表和可选 ChatOptions
 * Message 表示 system、user、assistant、tool 等不同角色的消息
 * PromptTemplate 负责把模板变量渲染成普通 user message 或 Prompt
 * SystemPromptTemplate 负责把模板变量渲染成 SystemMessage 或只包含 SystemMessage 的 Prompt
 *
 * <p>
 * 当前项目公开方法命名遵循 Spring AI 源码语义
 * render 返回渲染后的字符串，适用于 ChatClient.defaultSystem 等只需要纯文本的入口
 * create 返回 Prompt，适用于需要同时携带 Message 和 ChatOptions 的底层模型调用
 * createSystemMessage 返回 SystemMessage，适用于手动组装 Message 列表的底层调用
 *
 * <p>
 * Spring AI 1.1.7 的 PromptTemplate(Resource) 使用 Charset.defaultCharset 读取资源
 * Windows 环境可能不是 UTF-8，因此当前服务统一显式按 UTF-8 读取模板内容
 *
 * <p>
 * 默认模板变量使用 {variable} 语法
 * 如果模板中包含大量 JSON 示例，使用 renderWithAngleBrackets 和 <variable> 语法，避免 JSON 花括号与模板变量冲突
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
@Service
public class PromptTemplateService {

    private static final TemplateRenderer ANGLE_BRACKET_RENDERER = StTemplateRenderer.builder()
            .startDelimiterToken('<')
            .endDelimiterToken('>')
            .validationMode(ValidationMode.THROW)
            .build();

    /**
     * 渲染 prompt 纯文本
     *
     * <p>
     * 适用于 ChatClient.defaultSystem 等只需要字符串的场景
     *
     * @param templateId prompt 模板 ID
     * @return 渲染后的 prompt 文本
     */
    public String render(PromptTemplateId templateId) {
        return render(PromptTemplateRequest.of(templateId));
    }

    /**
     * 渲染 prompt 纯文本
     *
     * <p>
     * 适用于需要传入模板变量或 ChatOptions 的场景
     *
     * @param request prompt 模板请求
     * @return 渲染后的 prompt 文本
     */
    public String render(PromptTemplateRequest request) {
        validateVariables(request.templateId(), request.variables());
        return createPromptTemplate(request.templateId()).render(request.variables());
    }

    /**
     * 创建 system prompt Message
     *
     * <p>
     * 适用于需要显式构造 SystemMessage 并组装 Prompt 的场景
     *
     * @param request prompt 模板请求
     * @return system message
     */
    public Message createSystemMessage(PromptTemplateRequest request) {
        validateVariables(request.templateId(), request.variables());
        return createSystemPromptTemplate(request.templateId()).createMessage(request.variables());
    }

    /**
     * 创建 Spring AI Prompt
     *
     * <p>
     * 适用于需要同时传入 Message 列表和 ChatOptions 的底层模型调用场景
     *
     * @param request prompt 模板请求
     * @return Spring AI Prompt
     */
    public Prompt create(PromptTemplateRequest request) {
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
     * <p>
     * 适用于模板中包含大量 JSON 示例的场景，避免 JSON 花括号与 {variable} 模板变量冲突
     *
     * @param request prompt 模板请求
     * @return 渲染后的 prompt 文本
     */
    public String renderWithAngleBrackets(PromptTemplateRequest request) {
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
    private PromptTemplate createPromptTemplate(PromptTemplateId templateId) {
        return new PromptTemplate(templateContent(templateId));
    }

    /**
     * 创建 SystemPromptTemplate
     *
     * @param templateId prompt 模板 ID
     * @return SystemPromptTemplate
     */
    private SystemPromptTemplate createSystemPromptTemplate(PromptTemplateId templateId) {
        return new SystemPromptTemplate(templateContent(templateId));
    }

    /**
     * 加载 classpath prompt 资源
     *
     * @param templateId prompt 模板 ID
     * @return classpath 资源
     */
    private Resource resource(PromptTemplateId templateId) {
        return new ClassPathResource(templateId.path());
    }

    /**
     * 读取 UTF-8 prompt 模板内容
     *
     * <p>
     * Spring AI 1.1.7 的 PromptTemplate(Resource) 使用 Charset.defaultCharset 读取资源
     * Windows 环境可能不是 UTF-8，这里显式读取 UTF-8 后再交给 PromptTemplate(String) 渲染
     *
     * @param templateId prompt 模板 ID
     * @return prompt 模板内容
     */
    private String templateContent(PromptTemplateId templateId) {
        try {
            return resource(templateId).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read prompt template: " + templateId.path(), ex);
        }
    }

    /**
     * 校验模板必填变量
     *
     * @param templateId prompt 模板 ID
     * @param variables 模板变量
     */
    private void validateVariables(PromptTemplateId templateId, Map<String, Object> variables) {
        Set<String> missingVariables = templateId.requiredVariables()
                .stream()
                .filter(variable -> !variables.containsKey(variable))
                .collect(java.util.stream.Collectors.toSet());
        if (!missingVariables.isEmpty()) {
            throw new IllegalArgumentException("Missing prompt variables: " + missingVariables);
        }
    }

}
