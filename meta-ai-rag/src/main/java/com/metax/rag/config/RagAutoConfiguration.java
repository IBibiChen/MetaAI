package com.metax.rag.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * RagAutoConfiguration .
 *
 * <p>
 * RAG 模块基础配置，启用配置属性绑定和异步文档索引任务
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
@EnableAsync
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagAutoConfiguration {

}
