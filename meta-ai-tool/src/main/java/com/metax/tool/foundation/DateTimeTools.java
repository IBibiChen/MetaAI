package com.metax.tool.foundation;

import com.metax.tool.catalog.MetaToolNames;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * DateTimeTools .
 *
 * <p>
 * 无副作用日期时间工具，只读取当前请求时区并返回当前时间
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
public class DateTimeTools {

    /**
     * 获取当前日期时间
     *
     * <p>
     * 适用于全局默认工具和请求级显式工具，方法不访问业务数据、不写状态、不依赖租户或用户上下文
     *
     * @return 当前请求时区下的 ISO-8601 日期时间
     */
    @Tool(name = MetaToolNames.CURRENT_DATE_TIME, description = "获取当前用户时区的日期时间")
    public String currentDateTime() {
        ZoneId zoneId = LocaleContextHolder.getTimeZone().toZoneId();
        return OffsetDateTime.now(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
