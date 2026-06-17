package com.metax.tool.function.programmatic;

import com.metax.tool.context.MetaToolContextKeys;
import com.metax.tool.context.ToolContextAccessor;
import com.metax.tool.function.model.AuditToolRequest;
import com.metax.tool.function.model.DateTimeToolRequest;
import org.springframework.ai.chat.model.ToolContext;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * DateTimeFunctionTools .
 *
 * <p>
 * Spring AI 函数工具示例集合，演示 Function、Supplier、Consumer 和 BiFunction 四类函数入口
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
public class DateTimeFunctionTools {

    /**
     * 指定时区日期时间 Function
     *
     * <p>
     * Function 接收一个结构化请求对象并返回字符串，适合有明确入参和返回值的纯函数工具
     *
     * @return 指定时区日期时间函数
     */
    public Function<DateTimeToolRequest, String> zonedDateTimeFunction() {
        return request -> currentDateTime(request.zoneId());
    }

    /**
     * 当前 UTC 日期时间 Supplier
     *
     * <p>
     * Supplier 没有输入参数，Spring AI 会使用 Void 类型生成空入参 schema
     *
     * @return 当前 UTC 日期时间函数
     */
    public Supplier<String> currentUtcDateTimeSupplier() {
        return () -> currentDateTime("UTC");
    }

    /**
     * 审计 Consumer
     *
     * <p>
     * Consumer 只有输入参数没有返回值，适合演示官方支持形态，不建议初期绑定真实写操作
     *
     * @return 审计消费函数
     */
    public Consumer<AuditToolRequest> auditConsumer() {
        return request -> {
            // 教学示例刻意不写日志、不写数据库，避免把 Consumer 示例误解成可随意暴露的写操作
        };
    }

    /**
     * 带 ToolContext 的日期时间 BiFunction
     *
     * <p>
     * BiFunction 的第二个参数固定为 ToolContext，用于读取本轮请求注入的租户、用户或会话边界
     *
     * @return 带上下文的日期时间函数
     */
    public BiFunction<DateTimeToolRequest, ToolContext, String> contextualDateTimeFunction() {
        return (request, context) -> {
            String tenantId = ToolContextAccessor.stringValue(context, MetaToolContextKeys.TENANT_ID)
                    .orElse("anonymous");
            String userId = ToolContextAccessor.stringValue(context, MetaToolContextKeys.USER_ID)
                    .orElse("anonymous");
            return "tenantId = " + tenantId + "，userId = " + userId + "，dateTime = "
                    + currentDateTime(request.zoneId());
        };
    }

    private String currentDateTime(String zoneId) {
        ZoneId resolvedZoneId = ZoneId.of(zoneId);
        return OffsetDateTime.now(resolvedZoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
