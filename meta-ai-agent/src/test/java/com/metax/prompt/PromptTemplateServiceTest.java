package com.metax.prompt;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PromptTemplateServiceTest .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
class PromptTemplateServiceTest {

    private final PromptTemplateService promptTemplateService = new PromptTemplateService();

    /**
     * 默认系统提示词可以从 classpath 加载并渲染
     */
    @Test
    void shouldRenderDefaultChatSystemPrompt() {
        String prompt = promptTemplateService.render(PromptTemplateRequest.of(PromptTemplateId.CHAT_GENERAL_SYSTEM));

        assertThat(prompt).contains("MetaAI 的默认对话助手");
    }

    /**
     * RAG 系统提示词可以从 classpath 加载并渲染
     */
    @Test
    void shouldRenderDefaultRagSystemPrompt() {
        String prompt = promptTemplateService.render(PromptTemplateRequest.of(PromptTemplateId.RAG_RETRIEVAL_SYSTEM));

        assertThat(prompt).contains("RAG 检索增强助手");
    }

    /**
     * 幽默系统提示词可以从 classpath 加载并渲染
     */
    @Test
    void shouldRenderHumorChatSystemPrompt() {
        String prompt = promptTemplateService.render(PromptTemplateRequest.of(PromptTemplateId.CHAT_HUMOR_SYSTEM));

        assertThat(prompt)
                .contains("专业、严谨、友好、幽默")
                .contains("海盗的口吻");
    }

    /**
     * 故事示例模板可以按变量渲染
     */
    @Test
    void shouldRenderStoryUserPrompt() {
        String prompt = promptTemplateService.render(PromptTemplateRequest.of(PromptTemplateId.EXAMPLE_STORY_USER, Map.of(
                "topic", "Spring AI",
                "outputFormat", "Markdown",
                "wordCount", 300
        )));

        assertThat(prompt).contains("讲一个关于Spring AI的故事并以Markdown格式输出，字数在300左右");
    }

    /**
     * 系统提示词可以创建为 Message
     */
    @Test
    void shouldCreateSystemMessage() {
        assertThat(promptTemplateService.createSystemMessage(PromptTemplateRequest.of(PromptTemplateId.CHAT_GENERAL_SYSTEM)).getText())
                .contains("MetaAI 的默认对话助手");
    }

    /**
     * 普通提示词可以创建为 Prompt
     */
    @Test
    void shouldCreatePrompt() {
        assertThat(promptTemplateService.create(PromptTemplateRequest.of(PromptTemplateId.EXAMPLE_STORY_USER, Map.of(
                "topic", "向量数据库",
                "outputFormat", "JSON",
                "wordCount", 200
        ))).getContents()).contains("向量数据库");
    }

    /**
     * 必填变量缺失时快速失败
     */
    @Test
    void shouldRejectMissingVariables() {
        assertThatThrownBy(() -> promptTemplateService.render(PromptTemplateRequest.of(PromptTemplateId.EXAMPLE_STORY_USER, Map.of(
                "topic", "Spring AI"
        ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing prompt variables");
    }

}
