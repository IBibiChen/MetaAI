package com.metax.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClientConfig .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/28
 */
@Configuration
public class ChatClientConfig {

    /**
     * ChatClient 的配置遵循 "全局默认 + 调用时覆盖" 的设计哲学
     * -
     * ChatClient.builder() 提供完整的配置能力
     * - defaultSystem 设置系统提示词
     * - defaultOptions 设置默认模型参数
     * - defaultAdvisors 增强能力
     * * - MessageChatMemoryAdvisor 多轮对话记忆
     * * - QuestionAnswerAdvisor RAG 检索增强
     * * - SafeGuardAdvisor 内容安全过滤
     * * - SimpleLoggerAdvisor 打印请求/响应日志(建议添加在链的末端、logging.level.org.springframework.ai.chat.client.advisor=DEBUG)
     *
     * @param builder ChatClient.Builder
     * @return ChatClient
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 ChatMemory chatMemory) {

        return builder
                // 系统提示词
                .defaultSystem("""
                        你是一个专业、严谨、友好、幽默的智能助手。
                        回答问题时请优先使用中文，请用海盗的口吻回答问题。
                        如果不确定，请明确说明不确定，不要编造。
                        """)

                // 模型参数

                // Advisor 增强器(顺序重要)
                // 请求 → Memory → RAG → SafeGuard → Logger → Model
                // 响应 ← Memory ← RAG ← SafeGuard ← Logger ← Model
                .defaultAdvisors(
                        // new MessageChatMemoryAdvisor(chatMemory),
                        // new QuestionAnswerAdvisor(vectorStore, SearchRequest.defaults().withTopK(3)),
                        // new SimpleLoggerAdvisor()

                        // 对话记忆 Advisor
                        // 传递: .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, "tenantId:userId:sessionId"))
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        // QuestionAnswerAdvisor.builder(vectorStore).build(),

                        // 日志 Advisor
                        SimpleLoggerAdvisor.builder().build()
                )
                .build();

    }

    // @Bean
    // public ChatClient openAiChatClient(OpenAiChatModel chatModel) {
    //     return ChatClient.builder(chatModel)
    //             .defaultAdvisors(new SimpleLoggerAdvisor())
    //             .build();
    //     return ChatClient.create(chatModel);
    // }

    // @Bean
    // public ChatClient anthropicChatClient(AnthropicChatModel chatModel) {
    //     return ChatClient.create(chatModel);
    // }

}
