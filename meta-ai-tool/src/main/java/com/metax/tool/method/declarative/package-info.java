/**
 * 声明式方法工具
 *
 * <p>
 * 声明式方法工具使用 @Tool 标注方法，使用 @ToolParam 描述参数
 * 调用侧可以通过 ChatClient.tools 直接传入工具对象，也可以先用 ToolCallbacks.from 转成 ToolCallback 后再传入
 *
 * @Tool(resultConverter = ...) 可以为单个方法指定 ToolCallResultConverter
 * 该路径适合把普通方法返回值转换成更稳定的模型可读文本
 *
 * <p>
 * 该路径最适合静态、稳定、可读性优先的工具
 * 如果工具名称、描述、schema 或 returnDirect 需要运行期动态决定，应改用 MethodToolCallback
 */
package com.metax.tool.method.declarative;
