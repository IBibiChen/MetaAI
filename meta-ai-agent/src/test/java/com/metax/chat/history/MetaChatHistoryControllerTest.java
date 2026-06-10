package com.metax.chat.history;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MetaChatHistoryControllerTest .
 *
 * <p>
 * 完整聊天历史接口参数校验测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
@WebMvcTest(MetaChatHistoryController.class)
class MetaChatHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MetaChatHistoryService metaChatHistoryService;

    /**
     * 缺少 chatId 应返回统一参数错误
     *
     * @throws Exception MVC 调用异常
     */
    @Test
    void shouldReturnCommonResultWhenchatIdMissing() throws Exception {
        mockMvc.perform(get("/v1/chat/history/page"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("chatId 不能为空"));
    }

    /**
     * 非法分页参数应返回统一参数错误
     *
     * @throws Exception MVC 调用异常
     */
    @Test
    void shouldReturnCommonResultWhenPageSizeInvalid() throws Exception {
        mockMvc.perform(get("/v1/chat/history/page")
                        .param("chatId", "c1")
                        .param("size", "501"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("size 不能大于 500"));
    }

}
