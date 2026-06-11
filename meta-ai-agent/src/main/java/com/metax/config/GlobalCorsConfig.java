package com.metax.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * GlobalCorsConfig .
 *
 * <p>
 * 使用 CorsFilter 在 Servlet Filter 层处理跨域
 * 确保所有响应 (包括 404 和异常) 都携带正确的 CORS 头
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/2/26
 */
@Configuration
public class GlobalCorsConfig {

    /**
     * 全局 CORS Filter 注册 Bean
     *
     * <p>
     * 用于离线单机部署和本地调试场景，在 Servlet Filter 层统一处理跨域预检和异常响应跨域头
     * 关键绑定关系为 CorsFilter 与 /** 全路径 CORS 规则
     *
     * @return CORS Filter
     */
    @Bean
    public CorsFilter corsFilter() {
        // 离线单机部署默认使用宽松跨域策略，便于本机前端、调试页面和接口工具直接访问
        CorsConfiguration config = new CorsConfiguration();

        // 离线单机部署默认允许所有来源
        config.addAllowedOriginPattern("*");
        // 允许所有请求头
        config.addAllowedHeader("*");
        // 允许所有 HTTP 方法
        config.addAllowedMethod("*");
        // 暴露 Content-Disposition 头 (文件下载场景需要)
        config.addExposedHeader("Content-Disposition");
        // 预检请求缓存 1800 秒
        config.setMaxAge(1800L);

        // CorsFilter 绑定到所有路径，确保 404、异常响应和预检请求都走同一套 CORS 处理
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 对所有路径生效
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
