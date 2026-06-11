package com.metax.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * MetaAsyncConfig .
 *
 * <p>
 * MetaAI 统一异步任务配置，集中管理文档索引、会话文件索引等后台任务线程池
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/10
 */
@EnableAsync
@Configuration
public class MetaAsyncConfig {

    /**
     * 应用级异步任务线程池
     * -
     * 核心线程数: 5
     * 最大线程数: 10
     * 队列容量: 100
     * 拒绝策略: CallerRunsPolicy (调用者线程执行)
     * <p>
     * 适用于 RAG 文档索引、会话文件临时索引等耗时后台任务
     * -
     * Async("taskExecutor") 会绑定到该线程池，避免使用 Spring 默认无界执行器
     *
     * @return 线程池执行器
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数用于承载常规后台索引并发
        executor.setCorePoolSize(5);
        // 最大线程数用于短时吸收 OCR、embedding 等耗时任务峰值
        executor.setMaxPoolSize(10);
        // 队列容量限制积压任务数量，避免大批量上传无限占用内存
        executor.setQueueCapacity(100);
        // 线程名前缀便于日志和线程 dump 中定位 MetaAI 异步任务
        executor.setThreadNamePrefix("meta-async-");
        // 队列满时由调用线程执行，给上游自然背压而不是直接丢任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 非核心线程空闲 60 秒后回收
        executor.setKeepAliveSeconds(60);
        // 应用关闭时等待已提交任务完成，避免索引状态半途丢失
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 最多等待 60 秒完成关闭前的后台任务
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * MVC 异步请求线程池
     *
     * <p>
     * 适用于 Flux ServerSentEvent、Callable、DeferredResult 等 Spring MVC 异步返回值
     * 关键绑定关系为 MetaWebMvcConfig.configureAsyncSupport 的 AsyncTaskExecutor
     *
     * @return MVC 异步请求线程池
     */
    @Bean(name = "mvcAsyncTaskExecutor")
    public AsyncTaskExecutor mvcAsyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数承载常规 SSE 和模型流式响应
        executor.setCorePoolSize(4);
        // 最大线程数用于吸收短时并发请求峰值
        executor.setMaxPoolSize(16);
        // 队列容量限制异步响应积压，避免请求无限堆积
        executor.setQueueCapacity(200);
        // 线程名前缀便于区分 MVC async 和后台索引任务
        executor.setThreadNamePrefix("meta-mvc-async-");
        // 队列满时由调用线程执行，形成自然背压
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
