package com.metax.external.adapter.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * ExternalAdapterAutoConfig .
 *
 * <p>
 * 第三方系统资料库同步模块自动配置
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/11
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(ExternalAdapterProperties.class)
@ConditionalOnProperty(name = "metax.external-adapter.enabled", havingValue = "true")
public class ExternalAdapterAutoConfig {

    /**
     * 外部文件服务 WebClient
     *
     * <p>
     * 适用于第三方系统文件下载，绑定 metax.external-adapter.file-service.host
     *
     * @param builder    WebClient Builder
     * @param properties 第三方系统同步配置
     * @return 外部文件服务 WebClient
     */
    @Bean
    public WebClient externalFileWebClient(WebClient.Builder builder, ExternalAdapterProperties properties) {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(
                        Math.toIntExact(properties.getFileService().getMaxInMemorySize().toBytes())))
                .build();
        return builder.baseUrl(properties.getFileService().getHost())
                .exchangeStrategies(strategies)
                .build();
    }

    /**
     * 本应用对象存储文档 API WebClient
     *
     * <p>
     * 适用于调用 /v1/storage/documents/upload，绑定当前 MetaAI Agent HTTP 地址
     *
     * @param builder    WebClient Builder
     * @param properties 第三方系统同步配置
     * @return 对象存储文档 API WebClient
     */
    @Bean
    public WebClient externalStorageWebClient(WebClient.Builder builder, ExternalAdapterProperties properties) {
        return builder.baseUrl(properties.getStorageApi().getBaseUrl()).build();
    }
}
