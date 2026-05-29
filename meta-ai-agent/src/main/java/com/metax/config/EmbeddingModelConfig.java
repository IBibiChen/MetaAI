package com.metax.config;

import org.springframework.context.annotation.Configuration;

/**
 * EmbeddingModelConfig .
 *
 * <p>
 * 三套向量模型 (DashScope / Ollama / OpenAI 兼容 vLLM、TEI 等) 均由各自 starter 自动装配,
 * 通过 spring.ai.model.embedding 单选开关 (dashscope | ollama | openai) 激活其中一套, 配置见 application.properties
 *
 * <p>
 * 为何不手动 @Bean: embedding 与向量库强绑定, 一个索引只能用一种 embedding, 切换需重建索引, 本质是单选语义;
 * 单选开关恰好匹配该约束, 切换 provider 只改配置值、无需改代码, 且自动装配会处理 OpenAI embedding 独立 base-url 等细节
 *
 * <p>
 * 单选生效时同一时间只装配一个 EmbeddingModel bean, 故 VectorStore 自动注入无歧义, 无需 @Primary
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/28
 */
@Configuration
public class EmbeddingModelConfig {

}
