package com.metax.tool.reference;

import com.metax.tool.specification.ToolCallbackDefinitionView;

import java.util.List;

/**
 * ToolReferenceResponse .
 *
 * <p>
 * 工具调用参考接口响应，统一返回模型回答、官方路径、工具定义和实现要点
 *
 * @param answer          模型回答或说明文本
 * @param path            本次演示的工具暴露路径
 * @param toolNames       本次演示暴露的工具名称
 * @param toolDefinitions 本次演示暴露的工具定义
 * @param notes           实现要点
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
public record ToolReferenceResponse(
        String answer,
        ToolReferencePath path,
        List<String> toolNames,
        List<ToolCallbackDefinitionView> toolDefinitions,
        List<String> notes
) {
}
