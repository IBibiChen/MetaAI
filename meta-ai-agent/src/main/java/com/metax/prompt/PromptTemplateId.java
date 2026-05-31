package com.metax.prompt;

import java.util.Set;

/**
 * PromptTemplateId .
 *
 * <p>
 * 统一管理项目内稳定的 prompt 模板标识
 * 每个模板标识负责绑定 classpath 路径和必填变量
 * 业务代码只引用 PromptTemplateId，不直接拼 classpath 路径，避免模板路径在多处散落
 *
 * <p>
 * prompt 命名规范
 * 目录按大场景分层，例如 chat、rag、tool、examples
 * 文件名使用 kebab-case，格式为 {scene}-{purpose}-{role}.st
 * enum 使用 UPPER_SNAKE_CASE，格式为 {SCENE}_{PURPOSE}_{ROLE}
 * scene 表示大场景，purpose 表示具体用途，role 表示消息角色
 *
 * <p>
 * role 取值建议固定为 system、user、assistant、tool
 * purpose 需要表达模板具体用途，例如 general、humor、retrieval、story
 * 新增模板时先确定 scene、purpose、role，再新增资源文件和 PromptTemplateId
 * 模板变量统一使用 camelCase，变量名必须与 Map<String, Object> 的 key 完全一致
 * 不使用 snake_case，除非模板明确对接外部 JSON 字段
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
public enum PromptTemplateId {

    CHAT_GENERAL_SYSTEM("prompts/chat/chat-general-system.st", Set.of()),

    RAG_RETRIEVAL_SYSTEM("prompts/rag/rag-retrieval-system.st", Set.of()),

    CHAT_HUMOR_SYSTEM("prompts/chat/chat-humor-system.st", Set.of()),

    EXAMPLE_STORY_USER("prompts/examples/example-story-user.st", Set.of("topic", "outputFormat", "wordCount"));

    private final String path;

    private final Set<String> requiredVariables;

    PromptTemplateId(String path, Set<String> requiredVariables) {
        this.path = path;
        this.requiredVariables = requiredVariables;
    }

    /**
     * classpath 模板路径
     *
     * @return 模板路径
     */
    public String path() {
        return path;
    }

    /**
     * 必填变量集合
     *
     * @return 必填变量集合
     */
    public Set<String> requiredVariables() {
        return requiredVariables;
    }

}
