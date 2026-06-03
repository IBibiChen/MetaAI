package com.metax.common;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GlobalExceptionHandlerTest .
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
class GlobalExceptionHandlerTest {

    /**
     * 上传超限应返回明确大小限制
     */
    @Test
    void shouldReturnMaxUploadSizeMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        CommonResult<Void> result = handler.handleMaxUploadSize(new MaxUploadSizeExceededException(30 * 1024 * 1024));

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).isEqualTo("上传文件大小不能超过 30MB");
    }
}
