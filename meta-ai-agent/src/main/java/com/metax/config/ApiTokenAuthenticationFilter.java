package com.metax.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metax.common.CommonResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.PathContainer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * ApiTokenAuthenticationFilter .
 *
 * <p>
 * 开发期轻量 API Key 鉴权过滤器
 * API Key 通过 metax.ai.security.api-key 配置，默认值只用于本地开发
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/9
 */
@Component
public class ApiTokenAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Authorization Header 中 API Key 使用的 Bearer 前缀
     */
    private static final String AUTHORIZATION_PREFIX = "Bearer ";

    /**
     * 需要开发期 API Key 保护的接口路径规则
     *
     * <p>
     * GET 协议保持浏览器 EventSource 原生调用能力，不纳入此列表
     * 新增需要自定义 Header 或文件上传的 POST 接口时，优先追加到这里
     */
    private static final List<PathPattern> API_KEY_REQUIRED_PATTERNS = parsePatterns(List.of(
            "/v1/chat",
            "/v1/rag",
            "/v1/chat/files"
    ));

    /**
     * 开发期 API Key 原始值
     *
     * <p>
     * 请求校验时与 Authorization Header 中的 Bearer 凭证进行匹配
     */
    private final String apiKey;

    private final ObjectMapper objectMapper;

    /**
     * 创建开发期 API Key 鉴权过滤器
     *
     * @param apiKey       开发期 API Key
     * @param objectMapper JSON 序列化组件
     */
    public ApiTokenAuthenticationFilter(@Value("${metax.ai.security.api-key:sk-metax-123456}") String apiKey,
                                        ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行 API Key 鉴权
     *
     * <p>
     * 命中受保护 POST 路径时校验 Authorization Header，未命中时直接放行
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!shouldAuthenticate(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        // 前端 POST JSON 和文件上传统一使用 Bearer API Key，GET 协议保持原生 EventSource 能力
        String actualApiKey = resolveApiKey(request);
        if (!apiKey.equals(actualApiKey)) {
            writeUnauthorized(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 判断当前请求是否需要 API Key 鉴权
     *
     * <p>
     * 只保护 POST 接口，GET 协议不拦截，避免破坏浏览器原生 EventSource 调用
     *
     * @param request HTTP 请求
     * @return true 表示需要校验 Authorization Header
     */
    private boolean shouldAuthenticate(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        PathContainer path = ServletRequestPathUtils.parseAndCache(request).pathWithinApplication();
        return API_KEY_REQUIRED_PATTERNS.stream().anyMatch(pattern -> pattern.matches(path));
    }

    /**
     * 从 Authorization Header 中解析 API Key
     *
     * <p>
     * 只负责解析 Bearer 凭证，不负责判断凭证是否正确
     *
     * @param request HTTP 请求
     * @return API Key，Header 缺失或格式非法时返回 null
     */
    private String resolveApiKey(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(AUTHORIZATION_PREFIX)) {
            return null;
        }
        String actualApiKey = authorization.substring(AUTHORIZATION_PREFIX.length());
        return StringUtils.hasText(actualApiKey) ? actualApiKey : null;
    }

    /**
     * 写入统一未授权响应
     *
     * @param response HTTP 响应
     */
    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), CommonResult.error(HttpStatus.UNAUTHORIZED.value(), "未授权"));
    }

    /**
     * 将字符串路径规则预编译为 Spring PathPattern
     *
     * <p>
     * PathPattern 与 Spring MVC 6 路径匹配模型对齐，适合高频 Web 路径匹配
     *
     * @param paths 字符串路径规则
     * @return 预编译后的路径规则
     */
    private static List<PathPattern> parsePatterns(List<String> paths) {
        return paths.stream().map(PathPatternParser.defaultInstance::parse).toList();
    }
}
