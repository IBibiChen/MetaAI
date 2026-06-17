package com.metax.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MetaWebMvcConfig .
 *
 * <p>
 * Spring MVC 异步请求配置
 * 为 SSE 和异步 Controller 返回值提供专用线程池
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/11
 */
@Configuration
public class MetaWebMvcConfig implements WebMvcConfigurer {

    /**
     * MVC 异步请求超时时间
     */
    private static final long MVC_ASYNC_TIMEOUT_MILLIS = 300_000L;

    private final AsyncTaskExecutor mvcAsyncTaskExecutor;

    /**
     * 创建 Spring MVC 异步配置
     *
     * @param mvcAsyncTaskExecutor MVC 异步请求线程池
     */
    public MetaWebMvcConfig(AsyncTaskExecutor mvcAsyncTaskExecutor) {
        this.mvcAsyncTaskExecutor = mvcAsyncTaskExecutor;
    }

    /**
     * 配置 Spring MVC 异步支持
     *
     * <p>
     * 显式替换默认 SimpleAsyncTaskExecutor，避免流式响应在负载下创建无界线程
     *
     * @param configurer MVC 异步支持配置器
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcAsyncTaskExecutor);
        configurer.setDefaultTimeout(MVC_ASYNC_TIMEOUT_MILLIS);
    }

    /**
     * 配置前端 History 路由回退
     *
     * <p>
     * 前端页面由 Spring Boot 静态资源托管，浏览器直接刷新嵌入页或工作台路由时需要回退到 index.html
     * 这里只映射明确的前端路由，避免把 /v1、/internal、Knife4j 或静态资源 404 错误误转成页面
     * 不使用根路径单层或多层全局通配规则，后端 API 404 必须保留原始语义
     *
     * @param registry MVC 视图控制器注册器
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/chat").setViewName("forward:/index.html");
        registry.addViewController("/documents").setViewName("forward:/index.html");
        registry.addViewController("/embed/chat").setViewName("forward:/index.html");
    }
}
