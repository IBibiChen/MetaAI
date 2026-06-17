package com.metax.tool.argument;

/**
 * ToolAuditArguments .
 *
 * <p>
 * 工具参数增强示例入参，演示 AugmentedToolCallbackProvider 如何把额外 record 字段加入工具 input schema
 *
 * @param requestId  调用侧审计请求 ID
 * @param operatorId 调用侧操作人 ID
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
public record ToolAuditArguments(
        String requestId,
        String operatorId
) {
}
