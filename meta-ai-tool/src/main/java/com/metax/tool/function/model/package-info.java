/**
 * 函数工具模型
 *
 * <p>
 * 函数工具推荐使用 record 承载结构化入参
 * Spring AI 会基于入参类型生成 JSON Schema，模型再根据 schema 生成工具调用参数
 *
 * <p>
 * 初期不要把 primitive、集合、Optional、异步类型或响应式类型作为函数工具的直接输入输出
 * 使用明确的 record 可以让 schema、注释和测试都更稳定
 */
package com.metax.tool.function.model;
