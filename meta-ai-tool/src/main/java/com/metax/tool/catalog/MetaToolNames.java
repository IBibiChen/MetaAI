package com.metax.tool.catalog;

/**
 * MetaToolNames .
 *
 * <p>
 * 项目内工具名称常量，避免客户端协议、allowlist 和 Spring AI 工具定义出现字符串漂移
 * 字段名服务 Java 代码可读性，常量值是模型可见的工具协议名
 * 一旦系统上线，修改常量值就是工具协议迁移，需要同步客户端、提示词、allowlist、测试和文档
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
public final class MetaToolNames {

    /**
     * 当前日期时间工具
     */
    public static final String CURRENT_DATE_TIME = "currentDateTime";

    /**
     * 声明式方法当前日期时间工具
     */
    public static final String METHOD_NOW = "methodNow";

    /**
     * 声明式方法指定时区日期时间工具
     */
    public static final String METHOD_ZONED = "methodZoned";

    /**
     * 声明式方法可选时区日期时间工具
     */
    public static final String METHOD_OPTIONAL_ZONE = "methodOptionalZone";

    /**
     * 声明式方法自定义结果转换日期时间工具
     */
    public static final String METHOD_CONVERTED = "methodConverted";

    /**
     * 编程式方法当前日期时间工具
     */
    public static final String METHOD_CALLBACK = "methodCallback";

    /**
     * 编程式方法自定义结果转换日期时间工具
     */
    public static final String METHOD_CALLBACK_CONVERTED = "methodCallbackConverted";

    /**
     * 编程式函数指定时区日期时间工具
     */
    public static final String FUNCTION_CALLBACK = "functionCallback";

    /**
     * 编程式 Supplier 当前日期时间工具
     */
    public static final String FUNCTION_SUPPLIER = "functionSupplier";

    /**
     * 编程式 Consumer 审计工具
     */
    public static final String FUNCTION_CONSUMER = "functionConsumer";

    /**
     * 编程式 BiFunction 上下文日期时间工具
     */
    public static final String FUNCTION_CONTEXT = "functionContext";

    /**
     * Spring Bean Function 指定时区日期时间工具
     */
    public static final String FUNCTION_BEAN = "functionBean";

    /**
     * Tool Specification 直接返回日期时间工具
     */
    public static final String SPEC_RETURN_DIRECT = "specReturnDirect";

    private MetaToolNames() {
    }
}
