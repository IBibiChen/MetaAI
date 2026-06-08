package com.metax.chat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MetaChatControllerTest .
 *
 * <p>
 * 聊天会话接口参数校验测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/4
 */
@WebMvcTest(MetaChatController.class)
class MetaChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MetaChatService metaChatService;

    /**
     * 会话列表缺少租户 ID 应返回统一参数错误
     *
     * @throws Exception MVC 调用异常
     */
    @Test
    void shouldReturnCommonResultWhenTenantIdMissing() throws Exception {
        mockMvc.perform(get("/v1/chats/page")
                        .param("userId", "u1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("tenantId 不能为空"));
    }

    /**
     * 会话重命名缺少标题应返回统一参数错误
     *
     * @throws Exception MVC 调用异常
     */
    @Test
    void shouldReturnCommonResultWhenTitleMissing() throws Exception {
        mockMvc.perform(patch("/v1/chats/1/title")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("title 不能为空"));
    }
}
