package com.metax.rag.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * MetaRetrievalAutoConfiguration .
 *
 * <p>
 * Meta Retrieval 模块基础配置，启用配置属性绑定
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
@Configuration
@EnableConfigurationProperties(MetaRetrievalProperties.class)
public class MetaRetrievalAutoConfiguration {

}
