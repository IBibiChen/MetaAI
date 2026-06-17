package com.metax.tool.method.declarative;

import com.metax.tool.catalog.MetaToolNames;
import com.metax.tool.specification.conversion.CustomToolCallResultConverter;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * DeclarativeDateTimeTools .
 *
 * <p>
 * Spring AI 声明式方法工具示例，演示 @Tool 和 @ToolParam 如何把普通 Java 方法暴露为模型可调用工具
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
public class DeclarativeDateTimeTools {

    /**
     * 获取当前请求时区日期时间
     *
     * <p>
     * 该方法演示最小声明式工具，只需要 @Tool 标注方法即可由 Spring AI 生成 ToolCallback
     *
     * @return 当前请求时区下的 ISO-8601 日期时间
     */
    @Tool(name = MetaToolNames.METHOD_NOW,
            description = "通过声明式 @Tool 方法获取当前用户时区的日期时间")
    public String currentDateTime() {
        ZoneId zoneId = LocaleContextHolder.getTimeZone().toZoneId();
        return OffsetDateTime.now(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /**
     * 获取指定时区日期时间
     *
     * <p>
     * 该方法演示 @ToolParam，参数说明会进入工具 input schema，帮助模型正确生成工具参数
     *
     * @param zoneId IANA 时区 ID，例如 Asia/Shanghai 或 UTC
     * @return 指定时区下的 ISO-8601 日期时间
     */
    @Tool(name = MetaToolNames.METHOD_ZONED,
            description = "通过声明式 @Tool 方法获取指定 IANA 时区的当前日期时间")
    public String currentDateTimeForZone(
            @ToolParam(description = "IANA 时区 ID，例如 Asia/Shanghai 或 UTC") String zoneId) {
        ZoneId resolvedZoneId = ZoneId.of(zoneId);
        return OffsetDateTime.now(resolvedZoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /**
     * 获取可选时区日期时间
     *
     * <p>
     * 该方法演示 @ToolParam(required = false)，模型可以省略 zoneId，由工具回退到当前请求时区
     *
     * @param zoneId 可选 IANA 时区 ID，空值时使用当前请求时区
     * @return 指定时区或当前请求时区下的 ISO-8601 日期时间
     */
    @Tool(name = MetaToolNames.METHOD_OPTIONAL_ZONE,
            description = "通过声明式 @Tool 方法获取当前日期时间，可选指定 IANA 时区")
    public String currentDateTimeForOptionalZone(
            @ToolParam(description = "可选 IANA 时区 ID，例如 Asia/Shanghai 或 UTC",
                    required = false) String zoneId) {
        ZoneId resolvedZoneId = zoneId == null || zoneId.isBlank()
                ? LocaleContextHolder.getTimeZone().toZoneId()
                : ZoneId.of(zoneId);
        return OffsetDateTime.now(resolvedZoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /**
     * 使用自定义结果转换器获取指定时区日期时间
     *
     * <p>
     * 该方法演示 @Tool(resultConverter = ...) 声明式扩展点
     * Spring AI 会在工具方法返回后使用 CustomToolCallResultConverter 把结果转换成模型可读取字符串
     *
     * @param zoneId IANA 时区 ID，例如 Asia/Shanghai 或 UTC
     * @return 指定时区下的 ISO-8601 日期时间
     */
    @Tool(name = MetaToolNames.METHOD_CONVERTED,
            description = "通过声明式 @Tool 方法获取指定 IANA 时区的当前日期时间，并使用自定义结果转换器",
            resultConverter = CustomToolCallResultConverter.class)
    public String currentDateTimeWithCustomResultConverter(
            @ToolParam(description = "IANA 时区 ID，例如 Asia/Shanghai 或 UTC") String zoneId) {
        ZoneId resolvedZoneId = ZoneId.of(zoneId);
        return OffsetDateTime.now(resolvedZoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
