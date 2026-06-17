package com.metax.tool.foundation;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DateTimeToolsTest .
 *
 * <p>
 * 当前日期时间工具单元测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
class DateTimeToolsTest {

    @Test
    void currentDateTimeShouldReturnIsoOffsetDateTime() {
        DateTimeTools tools = new DateTimeTools();

        String value = tools.currentDateTime();

        assertThat(value).isNotBlank();
        assertThat(OffsetDateTime.parse(value)).isNotNull();
    }
}
