/**
 * MetaAI 通用工具模块
 *
 * <p>
 * 本包是 MetaAI 对 Spring AI Tools 官方工具调用体系的通用能力模块
 * 负责沉淀跨业务可复用的工具定义、工具契约、上下文读取、工具执行策略和官方路径参考代码
 *
 * <p>
 * meta-ai-tool 只放无副作用、无权限边界、跨业务复用的基础工具和工具调用基础设施
 * 具体业务工具应放在 agent 或具体业务模块，并在业务模块中绑定权限、租户隔离、审计、幂等和异常处理
 *
 * <p>
 * 完整学习文档放在同包目录 README.md
 * 该文档按 Spring AI 官方 Tools 路径说明 Methods as Tools、Functions as Tools、Tool Specification、Tool Execution、Tool Resolution、Tool Argument Augmentation、ToolContext 和 ToolCallAdvisor
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
package com.metax.tool;
