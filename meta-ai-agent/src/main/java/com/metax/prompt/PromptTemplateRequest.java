package com.metax.prompt;

import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.Map;

/**
 * PromptTemplateRequest .
 *
 * <p>
 * prompt 模板请求对象，集中携带模板 ID、变量和可选模型参数
 * 同一个请求对象可用于 render、create Prompt、create Message 等模板处理场景
 * 后续需要支持 tenant、locale、scene、version 时优先扩展这里，不把参数散落到调用链
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
public record PromptTemplateRequest(
        PromptTemplateId templateId,
        Map<String, Object> variables,
        ChatOptions chatOptions
) {

    /**
     * 无变量、无模型参数的模板请求
     *
     * @param templateId prompt 模板 ID
     * @return prompt 模板请求
     */
    public static PromptTemplateRequest of(PromptTemplateId templateId) {
        return of(templateId, Map.of());
    }

    /**
     * 带变量、无模型参数的模板请求
     *
     * @param templateId prompt 模板 ID
     * @param variables  模板变量
     * @return prompt 模板请求
     */
    public static PromptTemplateRequest of(PromptTemplateId templateId, Map<String, Object> variables) {
        return new PromptTemplateRequest(templateId, variables, null);
    }

}
