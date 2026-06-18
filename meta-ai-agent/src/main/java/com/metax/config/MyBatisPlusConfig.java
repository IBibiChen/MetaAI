package com.metax.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatisPlusConfig .
 *
 * <p>
 * MyBatis Plus 基础配置
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/2
 */
@Configuration
public class MyBatisPlusConfig {

    /**
     * MyBatis Plus 分页插件
     *
     * <p>
     * MetaChatHistory 分页查询依赖该插件生成数据库分页 SQL
     * 当前应用按单主数据源运行，不固定数据库方言，由 MyBatis Plus 根据当前 JDBC URL 自动识别 MySQL / PostgreSQL 等数据库类型
     *
     * @return MyBatis Plus 拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor();
        paginationInterceptor.setOverflow(false);
        paginationInterceptor.setMaxLimit(500L);
        interceptor.addInnerInterceptor(paginationInterceptor);
        return interceptor;
    }
}
