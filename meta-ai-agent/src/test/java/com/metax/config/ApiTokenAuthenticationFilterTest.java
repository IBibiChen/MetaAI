package com.metax.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ApiTokenAuthenticationFilterTest .
 *
 * <p>
 * 开发期 API Key 鉴权过滤器单元测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/9
 */
class ApiTokenAuthenticationFilterTest {

    private static final String API_KEY = "sk-metax-123456";

    private final ApiTokenAuthenticationFilter filter = new ApiTokenAuthenticationFilter(API_KEY,
            new ObjectMapper().registerModule(new JavaTimeModule()));

    @Test
    void postJsonStreamShouldRejectMissingAuthorization() throws ServletException, IOException {
        MockHttpServletResponse response = doFilter("POST", "/v1/chat", null);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("未授权");
    }

    @Test
    void postJsonStreamShouldRejectWrongAuthorization() throws ServletException, IOException {
        MockHttpServletResponse response = doFilter("POST", "/v1/rag", "Bearer wrong-token");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("未授权");
    }

    @Test
    void postJsonStreamShouldRejectAuthorizationWithoutBearerPrefix() throws ServletException, IOException {
        MockHttpServletResponse response = doFilter("POST", "/v1/chat", API_KEY);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("未授权");
    }

    @Test
    void postJsonStreamShouldRejectBlankBearerValue() throws ServletException, IOException {
        MockHttpServletResponse response = doFilter("POST", "/v1/chat", "Bearer ");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("未授权");
    }

    @Test
    void postChatFilesShouldPassWithAuthorization() throws ServletException, IOException {
        MockHttpServletResponse response = doFilter("POST", "/v1/chat/files", "Bearer " + API_KEY);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void postJsonChatShouldRequireAuthorization() throws ServletException, IOException {
        MockHttpServletResponse response = doFilter("POST", "/v1/chat", null);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void postJsonRagShouldPassWithAuthorization() throws ServletException, IOException {
        MockHttpServletResponse response = doFilter("POST", "/v1/rag", "Bearer " + API_KEY);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void postJsonStreamShouldMatchWithContextPath() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/meta-ai/v1/chat");
        request.setContextPath("/meta-ai");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void getStreamShouldNotRequireAuthorization() throws ServletException, IOException {
        MockHttpServletResponse response = doFilter("GET", "/v1/chat", null);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void unprotectedPostShouldNotRequireAuthorization() throws ServletException, IOException {
        MockHttpServletResponse response = doFilter("POST", "/v1/chat/history", null);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse doFilter(String method,
                                             String path,
                                             String authorization) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
