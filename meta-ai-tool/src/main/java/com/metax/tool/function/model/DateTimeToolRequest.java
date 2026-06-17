package com.metax.tool.function.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * DateTimeToolRequest .
 *
 * <p>
 * 函数工具入参对象，Spring AI 会基于 record 结构生成工具 input schema
 *
 * @param zoneId IANA 时区 ID，例如 Asia/Shanghai 或 UTC
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
@JsonClassDescription("获取指定 IANA 时区当前日期时间的请求参数")
public record DateTimeToolRequest(
        @JsonPropertyDescription("IANA 时区 ID，例如 Asia/Shanghai 或 UTC")
        String zoneId
) {
}
