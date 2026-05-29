package com.metax.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DashScopeConfig .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/8
 */
@Configuration
public class DashScopeConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    /**
     * DashScope 底层 API client
     *
     * <p>
     * DashScope 基础设施场景，使用 spring.ai.dashscope.api-key 绑定 API key，供 DashScope 相关模型组件复用
     *
     * @return DashScopeApi
     */
    @Bean
    public DashScopeApi dashScopeApi() {
        return DashScopeApi.builder()
                .apiKey(apiKey)
                .build();
    }

}
