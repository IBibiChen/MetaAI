package com.metax.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4jConfig .
 *
 * <p>
 * Knife4j 接口文档配置，面向当前 MetaAI 智能问答和 RAG 调试接口
 * 页面入口为 /doc.html，OpenAPI JSON 入口为 /v3/api-docs
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/2
 */
@Configuration
public class Knife4jConfig {

    /**
     * MetaAI OpenAPI 基础信息
     *
     * <p>
     * 用于 Knife4j 页面顶部展示接口文档标题、版本和说明
     * 当前只描述 /v1 下的智能问答、RAG 检索和文档索引调试接口
     *
     * @return OpenAPI
     */
    @Bean
    public OpenAPI metaAiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MetaAI 接口文档")
                        .version("1.0.0")
                        .description("MetaAI 智能问答、RAG 文档索引和检索增强调试接口"));
    }

    /**
     * MetaAI v1 接口分组
     *
     * <p>
     * 只扫描 /v1/** 路径，避免后续 actuator 或内部接口混入业务调试文档
     *
     * @return GroupedOpenApi
     */
    @Bean
    public GroupedOpenApi metaAiV1Api() {
        return GroupedOpenApi.builder()
                .group("MetaAI v1")
                .pathsToMatch("/v1/**")
                .build();
    }
}
