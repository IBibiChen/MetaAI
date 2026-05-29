package com.metax.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * EmbeddingModelConfig .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/28
 */
@Configuration
public class EmbeddingModelConfig {

    @Primary
    @Bean
    public EmbeddingModel dashScopeEmbeddingModel(DashScopeApi dashScopeApi) {

        return new DashScopeEmbeddingModel(dashScopeApi);
    }

    @Bean
    public EmbeddingModel ollamaLocalEmbeddingModel(OllamaApi ollamaApi) {

        return OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .build();
    }

}
