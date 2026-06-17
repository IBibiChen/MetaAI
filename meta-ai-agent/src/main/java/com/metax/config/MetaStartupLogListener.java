package com.metax.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * MetaStartupLogListener .
 *
 * <p>
 * 应用完全启动后输出本地直连访问入口
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
@Slf4j
@Component
public class MetaStartupLogListener {

    /**
     * 应用完成启动并准备接收请求后输出关键入口日志
     *
     * <p>
     * ApplicationReadyEvent 触发时 Web 端口已经完成绑定，适合打印本地直连访问地址
     * 这里不打印 Token、Redis 密码、模型 Key 或外部服务地址，避免启动日志暴露敏感配置
     *
     * @param event Spring Boot 应用 ready 事件
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        Environment environment = event.getApplicationContext().getEnvironment();
        WebServerApplicationContext webContext = (WebServerApplicationContext) event.getApplicationContext();
        int port = webContext.getWebServer().getPort();
        String profiles = Arrays.toString(environment.getActiveProfiles());

        log.info("MetaAI 启动完成：port = {}，profiles = {}", port, profiles);
        log.info("MetaAI 页面入口：http://localhost:{}/chat", port);
        log.info("MetaAI 嵌入入口：http://localhost:{}/embed/chat?tenantId=t1&userId=u1&kbId=kb1", port);
        log.info("MetaAI 接口文档：http://localhost:{}/doc.html", port);
    }
}
