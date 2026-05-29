package com.metax.config;

import org.springframework.context.annotation.Configuration;

/**
 * EmbeddingModelConfig .
 *
 * <p>
 * 三套向量模型 (DashScope / Ollama / OpenAI 兼容 vLLM / TEI 等) 均由各自 starter 自动装配
 * 不设置 spring.ai.model.embedding 单选开关，让三套 EmbeddingModel 同时存在，使用时通过具体 bean 名显式指定
 *
 * <p>
 * 为何不手动 @Bean：保留官方自动装配的连接、重试、观测和 provider options 处理
 * 向量库与 embedding 的绑定在各 VectorStore 配置类中完成，每套 embedding 使用独立存储单元
 *
 * <p>
 * 存在多个 EmbeddingModel bean 后禁止裸类型注入，必须使用 @Qualifier 或具体类型 / bean 名区分
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/28
 */
@Configuration
public class EmbeddingModelConfig {

}
