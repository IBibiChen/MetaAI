package com.metax.prompt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

    private final PromptTemplateService promptTemplateService = promptTemplateService();

    @TempDir
    private Path tempDir;

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

    /**
     * 外部 prompt 存在时应优先覆盖 classpath 内置模板
     */
    @Test
    void shouldPreferExternalPromptTemplate() throws IOException {
        PromptTemplateService service = externalPromptService();
        Path promptFile = tempDir.resolve("chat/chat-general-system.st");
        Files.createDirectories(promptFile.getParent());
        Files.writeString(promptFile, "外部对话系统提示词", StandardCharsets.UTF_8);

        String prompt = service.render(PromptTemplateRequest.of(PromptTemplateId.CHAT_GENERAL_SYSTEM));

        assertThat(prompt).isEqualTo("外部对话系统提示词");
    }

    /**
     * 外部 prompt 缺失时应回退到 classpath 内置模板
     */
    @Test
    void shouldFallbackToClasspathPromptWhenExternalPromptMissing() {
        PromptTemplateService service = externalPromptService();

        String prompt = service.render(PromptTemplateRequest.of(PromptTemplateId.CHAT_GENERAL_SYSTEM));

        assertThat(prompt).contains("MetaAI 的默认对话助手");
    }

    /**
     * 外部 prompt 路径存在但不是普通文件时应快速失败
     */
    @Test
    void shouldRejectInvalidExternalPromptPath() throws IOException {
        PromptTemplateService service = externalPromptService();
        Files.createDirectories(tempDir.resolve("chat/chat-general-system.st"));

        assertThatThrownBy(() -> service.render(PromptTemplateRequest.of(PromptTemplateId.CHAT_GENERAL_SYSTEM)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("外部 prompt 模板不是可读取的普通文件");
    }

    /**
     * 外部 prompt 可以使用尖括号变量分隔符渲染
     */
    @Test
    void shouldRenderExternalPromptWithAngleBrackets() throws IOException {
        PromptTemplateService service = externalPromptService();
        Path promptFile = tempDir.resolve("examples/example-story-user.st");
        Files.createDirectories(promptFile.getParent());
        Files.writeString(promptFile, "主题：<topic>，格式：<outputFormat>，字数：<wordCount>", StandardCharsets.UTF_8);

        String prompt = service.renderWithAngleBrackets(PromptTemplateRequest.of(PromptTemplateId.EXAMPLE_STORY_USER, Map.of(
                "topic", "Spring AI",
                "outputFormat", "Markdown",
                "wordCount", 300
        )));

        assertThat(prompt).isEqualTo("主题：Spring AI，格式：Markdown，字数：300");
    }

    /**
     * prompt 查找位置为空时应使用中文异常提示
     */
    @Test
    void shouldRejectEmptyPromptLocationsWithChineseMessage() {
        PromptTemplateProperties properties = new PromptTemplateProperties();
        properties.setLocations(List.of());
        PromptTemplateService service = new PromptTemplateService(new PromptTemplateResolver(properties));

        assertThatThrownBy(() -> service.render(PromptTemplateRequest.of(PromptTemplateId.CHAT_GENERAL_SYSTEM)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prompt 模板查找位置不能为空");
    }

    /**
     * 不支持的 prompt location 应使用中文异常提示
     */
    @Test
    void shouldRejectUnsupportedPromptLocationWithChineseMessage() {
        PromptTemplateProperties properties = new PromptTemplateProperties();
        properties.setLocations(List.of("http://example.com/prompts/"));
        PromptTemplateService service = new PromptTemplateService(new PromptTemplateResolver(properties));

        assertThatThrownBy(() -> service.render(PromptTemplateRequest.of(PromptTemplateId.CHAT_GENERAL_SYSTEM)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的 prompt 模板位置");
    }

    /**
     * 所有位置都未命中时应使用中文异常提示
     */
    @Test
    void shouldRejectMissingPromptTemplateWithChineseMessage() {
        PromptTemplateProperties properties = new PromptTemplateProperties();
        properties.setLocations(List.of(tempDir.toUri().toString()));
        PromptTemplateService service = new PromptTemplateService(new PromptTemplateResolver(properties));

        assertThatThrownBy(() -> service.render(PromptTemplateRequest.of(PromptTemplateId.CHAT_GENERAL_SYSTEM)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未找到 prompt 模板");
    }

    /**
     * 创建使用临时外部目录的 PromptTemplateService
     *
     * @return PromptTemplateService
     */
    private PromptTemplateService externalPromptService() {
        PromptTemplateProperties properties = new PromptTemplateProperties();
        properties.setLocations(List.of(tempDir.toUri().toString(), "classpath:/prompts/"));
        return new PromptTemplateService(new PromptTemplateResolver(properties));
    }

    /**
     * 创建使用默认 locations 的 PromptTemplateService
     *
     * @return PromptTemplateService
     */
    private PromptTemplateService promptTemplateService() {
        return new PromptTemplateService(new PromptTemplateResolver(new PromptTemplateProperties()));
    }

}
