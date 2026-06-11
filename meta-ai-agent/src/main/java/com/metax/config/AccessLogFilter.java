package com.metax.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * AccessLogFilter .
 *
 * <p>
 * 离线单机部署场景的全链路访问日志过滤器
 * 负责生成请求关联 ID、记录请求参数、状态码、耗时和异常摘要
 * 执行顺序晚于 CORS Filter，早于 API Key 鉴权 Filter
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/11
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AccessLogFilter extends OncePerRequestFilter {

    /**
     * 请求关联 ID Header 名称
     */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /**
     * MDC 中保存请求关联 ID 的 key
     */
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    /**
     * 不记录访问日志的精确路径
     *
     * <p>
     * 文档入口和浏览器默认图标请求不属于业务调用，跳过后可以降低低价值日志噪声
     */
    private static final Set<String> SKIPPED_EXACT_PATHS = Set.of(
            "/doc.html",
            "/favicon.ico"
    );

    /**
     * 不记录访问日志的路径前缀
     *
     * <p>
     * OpenAPI、Swagger UI、静态资源和 actuator 访问频率高且业务价值低，默认不进入访问审计日志
     */
    private static final String[] SKIPPED_PREFIXES = {
            "/swagger-ui/",
            "/v3/api-docs",
            "/webjars/",
            "/actuator/"
    };

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (SKIPPED_EXACT_PATHS.contains(uri)) {
            return true;
        }
        for (String skippedPrefix : SKIPPED_PREFIXES) {
            if (uri.startsWith(skippedPrefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 记录完整请求链路访问日志
     *
     * <p>
     * 使用 Spring ContentCachingRequestWrapper 读取下游已经消费的请求体
     * 访问日志默认不缓存响应体，避免分页列表、下载和 SSE 响应造成日志噪声或额外内存占用
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        // 请求关联 ID 同时写入 MDC 和响应头，便于串联访问日志、业务日志和客户端排查信息
        String requestId = resolveRequestId(request);
        long startTime = System.currentTimeMillis();
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        // 请求体使用 Spring 官方缓存包装器，读取发生在下游消费之后，避免提前破坏 Controller 参数绑定
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request, Integer.MAX_VALUE);
        Exception failure = null;

        log.info("[访问开始] {} {} | clientIp = {}{}",
                request.getMethod(), resolveRequestPath(request), resolveClientIp(request),
                resolveContentTypeLogSegment(request.getContentType()));

        try {
            // 响应体直接透传，访问结束日志只保留链路索引信息和请求参数
            filterChain.doFilter(requestWrapper, response);
            logAccessCompleted(requestWrapper, response, startTime, null);
        } catch (IOException | ServletException | RuntimeException ex) {
            // 异常链路记录访问审计后继续抛出，保留全局异常处理和容器错误处理语义
            failure = ex;
            logAccessCompleted(requestWrapper, response, startTime, ex);
            throw ex;
        } finally {
            // Filter 线程可能被复用，必须清理 MDC，避免 requestId 串到后续请求
            MDC.remove(REQUEST_ID_MDC_KEY);
            if (failure != null) {
                log.debug("请求异常已继续抛出：type = {}", failure.getClass().getName());
            }
        }
    }

    /**
     * 记录访问完成日志
     *
     * @param requestWrapper ContentCaching 请求包装器
     * @param response       HTTP 响应
     * @param startTime      请求开始时间
     * @param ex             请求链路异常
     */
    private void logAccessCompleted(ContentCachingRequestWrapper requestWrapper,
                                    HttpServletResponse response,
                                    long startTime,
                                    Exception ex) {
        long duration = System.currentTimeMillis() - startTime;
        String requestParamsLogSegment = resolveRequestParamsLogSegment(requestWrapper);

        if (ex == null) {
            log.info("[访问结束] {} {} | status = {} | durationMs = {}{}",
                    requestWrapper.getMethod(), resolveRequestPath(requestWrapper), response.getStatus(), duration,
                    requestParamsLogSegment);
            return;
        }

        log.warn("[访问异常] {} {} | status = {} | durationMs = {} | exception = {}: {}{}",
                requestWrapper.getMethod(), resolveRequestPath(requestWrapper), response.getStatus(), duration,
                ex.getClass().getSimpleName(), ex.getMessage(), requestParamsLogSegment);
    }

    /**
     * 解析请求关联 ID
     *
     * @param request HTTP 请求
     * @return 请求关联 ID
     */
    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (StringUtils.hasText(requestId)) {
            return requestId;
        }
        return generateShortRequestId();
    }

    /**
     * 生成短请求关联 ID
     *
     * <p>
     * 8 位十六进制 ID 适合离线单机日志排查，配合时间窗口可以降低完整 UUID 对日志扫描的干扰
     *
     * @return 8 位请求关联 ID
     */
    private String generateShortRequestId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * 解析请求路径
     *
     * @param request HTTP 请求
     * @return 请求路径
     */
    private String resolveRequestPath(HttpServletRequest request) {
        return request.getRequestURI();
    }

    /**
     * 解析客户端 IP
     *
     * @param request HTTP 请求
     * @return 客户端 IP
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    /**
     * 解析 Content-Type 日志片段
     *
     * <p>
     * 访问日志只保留媒体类型，boundary、charset 等协议参数会制造噪声且不影响链路排查
     *
     * @param contentType Content-Type Header 值
     * @return Content-Type 日志片段
     */
    private String resolveContentTypeLogSegment(String contentType) {
        String mediaType = resolveMediaType(contentType);
        if (!StringUtils.hasText(mediaType)) {
            return "";
        }
        return " | contentType = " + mediaType;
    }

    /**
     * 解析请求参数日志片段
     *
     * <p>
     * JSON、表单等请求体参数和 URL 查询参数统一输出为 params，避免暴露 Servlet 底层命名
     *
     * @param requestWrapper ContentCaching 请求包装器
     * @return 请求参数日志片段
     */
    private String resolveRequestParamsLogSegment(ContentCachingRequestWrapper requestWrapper) {
        String bodyParams = resolveBodyParams(requestWrapper);
        if (StringUtils.hasText(bodyParams)) {
            return " | params = " + bodyParams;
        }
        String urlParams = requestWrapper.getQueryString();
        if (StringUtils.hasText(urlParams)) {
            return " | params = " + urlParams;
        }
        return "";
    }

    /**
     * 解析请求体参数
     *
     * <p>
     * multipart 只记录类型标识，二进制内容只记录 binary 标识，文本请求体按请求字符集原文写入日志
     *
     * @param requestWrapper ContentCaching 请求包装器
     * @return 请求体参数
     */
    private String resolveBodyParams(ContentCachingRequestWrapper requestWrapper) {
        String mediaType = resolveMediaType(requestWrapper.getContentType());
        if (mediaType != null && mediaType.startsWith("multipart/")) {
            return "[multipart/form-data]";
        }
        if (mediaType != null && isBinaryContent(mediaType)) {
            return "[binary]";
        }
        byte[] body = getCachedRequestBody(requestWrapper);
        if (body.length == 0) {
            return null;
        }
        return new String(body, resolveCharset(requestWrapper.getCharacterEncoding()));
    }

    /**
     * 解析媒体类型
     *
     * <p>
     * Content-Type 可能包含 boundary、charset 等协议参数，访问日志只保留 type/subtype
     *
     * @param contentType Content-Type Header 值
     * @return 小写媒体类型
     */
    private String resolveMediaType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return null;
        }
        String mediaType = contentType.split(";", 2)[0].trim();
        if (!StringUtils.hasText(mediaType)) {
            return null;
        }
        return mediaType.toLowerCase(Locale.ROOT);
    }

    /**
     * 解析字符集
     *
     * @param characterEncoding Servlet 字符集名称
     * @return 字符集对象
     */
    private Charset resolveCharset(String characterEncoding) {
        if (!StringUtils.hasText(characterEncoding)) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(characterEncoding);
        } catch (Exception ex) {
            return StandardCharsets.UTF_8;
        }
    }

    /**
     * 判断是否为二进制内容
     *
     * <p>
     * 访问日志只把 text、json、xml 和表单内容按文本输出，其余类型统一按 binary 标识记录
     *
     * @param contentType Content-Type Header 值
     * @return true 表示不适合按文本写入访问日志
     */
    private boolean isBinaryContent(String contentType) {
        String normalizedContentType = contentType.toLowerCase(Locale.ROOT);
        boolean loggableTextContent = normalizedContentType.startsWith("text/")
                || normalizedContentType.contains("json")
                || normalizedContentType.contains("xml")
                || normalizedContentType.contains("x-www-form-urlencoded");
        return !loggableTextContent;
    }

    /**
     * 获取缓存请求体
     *
     * <p>
     * 鉴权失败或 404 等链路可能不会消费 body，访问日志在链路结束后补读剩余 body 用于审计
     *
     * @param requestWrapper ContentCaching 请求包装器
     * @return 请求体字节
     */
    private byte[] getCachedRequestBody(ContentCachingRequestWrapper requestWrapper) {
        byte[] body = requestWrapper.getContentAsByteArray();
        if (body.length > 0 || !mayHaveRequestBody(requestWrapper)) {
            return body;
        }
        try {
            requestWrapper.getInputStream().readAllBytes();
            return requestWrapper.getContentAsByteArray();
        } catch (IOException ex) {
            log.debug("补读请求体失败：type = {}，message = {}", ex.getClass().getSimpleName(), ex.getMessage());
            return body;
        }
    }

    /**
     * 判断请求是否可能包含 body
     *
     * @param request HTTP 请求
     * @return true 表示请求可能包含 body
     */
    private boolean mayHaveRequestBody(HttpServletRequest request) {
        return request.getContentLengthLong() > 0 || request.getHeader(HttpHeaders.TRANSFER_ENCODING) != null;
    }
}
