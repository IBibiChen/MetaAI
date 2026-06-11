package com.metax.storage;

import com.metax.storage.response.StorageDocumentDownloadResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * StorageDocumentControllerTest .
 *
 * <p>
 * 对象存储文档接口参数校验测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
@WebMvcTest(StorageDocumentController.class)
class StorageDocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StorageDocumentService storageDocumentService;

    /**
     * 上传缺少文件应返回统一参数错误
     *
     * @throws Exception MVC 调用异常
     */
    @Test
    void shouldReturnCommonResultWhenUploadFileMissing() throws Exception {
        mockMvc.perform(multipart("/v1/storage/documents/upload")
                        .param("tenantId", "t1")
                        .param("kbId", "kb1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("file 不能为空"));
    }

    /**
     * 分页缺少租户 ID 应返回统一参数错误
     *
     * @throws Exception MVC 调用异常
     */
    @Test
    void shouldReturnCommonResultWhenTenantIdMissing() throws Exception {
        mockMvc.perform(get("/v1/storage/documents/page")
                        .param("kbId", "kb1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("tenantId 不能为空"));
    }

    /**
     * 索引缺少知识库 ID 应返回统一参数错误
     *
     * @throws Exception MVC 调用异常
     */
    @Test
    void shouldReturnCommonResultWhenKbIdMissing() throws Exception {
        mockMvc.perform(post("/v1/storage/documents/doc-1/index")
                        .param("tenantId", "t1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("kbId 不能为空"));
    }

    /**
     * 裸 documentId 下载应返回文件流
     *
     * @throws Exception MVC 调用异常
     */
    @Test
    void shouldDownloadByGlobalDocumentId() throws Exception {
        when(storageDocumentService.download("doc-1")).thenReturn(new StorageDocumentDownloadResponse("demo.txt",
                "text/plain", 5L, new ByteArrayInputStream("hello".getBytes())));

        mockMvc.perform(get("/v1/storage/documents/download/doc-1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(header().string("Content-Length", "5"));
    }

    /**
     * 删除文档应透传租户、知识库和文档 ID
     *
     * @throws Exception MVC 调用异常
     */
    @Test
    void shouldDeleteDocumentWithScope() throws Exception {
        mockMvc.perform(delete("/v1/storage/documents/doc-1")
                        .param("tenantId", "t1")
                        .param("kbId", "kb1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(storageDocumentService).delete("t1", "kb1", "doc-1");
    }
}
