/**
 * 编程式函数工具
 *
 * <p>
 * 本包演示 FunctionToolCallback.builder 的官方用法
 * builder 至少需要工具名、函数对象和 inputType，description、ToolMetadata 和 ToolCallResultConverter 应按工具风险显式补齐
 * Function 工具没有 @Tool(resultConverter = ...) 注解路径
 * 因此自定义结果转换器应通过 FunctionToolCallback.builder().toolCallResultConverter(...) 绑定
 *
 * <p>
 * Function 适合有输入有输出的纯函数
 * Supplier 适合无输入工具
 * Consumer 适合无返回值动作，但生产系统必须谨慎处理副作用
 * BiFunction 适合读取 ToolContext 中的执行期上下文
 */
package com.metax.tool.function.programmatic;
