package com.metax.tool.method.programmatic;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * ProgrammaticDateTimeTools .
 *
 * <p>
 * Spring AI 编程式方法工具示例，方法本身不使用 @Tool，由 MethodToolCallback 在外部包装成工具
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
public class ProgrammaticDateTimeTools {

    /**
     * 获取指定时区日期时间
     *
     * <p>
     * 这是一个普通 Java 方法，它不知道自己会被模型调用
     * 是否暴露给模型完全由 MethodToolCallback 工厂决定
     *
     * @param zoneId IANA 时区 ID，例如 Asia/Shanghai 或 UTC
     * @return 指定时区下的 ISO-8601 日期时间
     */
    public String currentDateTime(String zoneId) {
        ZoneId resolvedZoneId = ZoneId.of(zoneId);
        return OffsetDateTime.now(resolvedZoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
