package com.metax.tool.function.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * AuditToolRequest .
 *
 * <p>
 * Consumer 函数工具入参对象，用于演示无返回值函数工具的 schema 生成方式
 *
 * @param eventName 审计事件名称
 * @param detail    审计事件详情
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
@JsonClassDescription("记录轻量级工具审计事件的请求参数")
public record AuditToolRequest(
        @JsonPropertyDescription("审计事件名称")
        String eventName,
        @JsonPropertyDescription("审计事件详情")
        String detail
) {
}
