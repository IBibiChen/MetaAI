/**
 * 工具目录治理包
 *
 * <p>
 * 本包不直接对应 Spring AI 官方文档中的某一个工具实现章节
 * 它负责集中维护项目内工具名称、风险分类和后续工具目录治理需要的稳定常量
 *
 * <p>
 * MetaToolNames 是模型工具协议的名称来源
 * 工具名称一旦进入 ChatClient、allowlist、测试或外部文档，就不能因为类名或包名重构而随意变化
 * 不要把 catalog 理解成 Spring AI ToolDefinition
 * ToolDefinition 是模型可见工具契约，catalog 只是项目内工具名称和分类的治理入口
 *
 * <p>
 * MetaToolCategory 用于表达工具风险边界
 * 后续接入工具市场、权限面板或业务工具准入策略时，可以继续沿用该分类
 */
package com.metax.tool.catalog;
