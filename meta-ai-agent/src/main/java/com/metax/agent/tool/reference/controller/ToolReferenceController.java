package com.metax.agent.tool.reference.controller;

import com.metax.agent.tool.reference.service.ToolReferenceService;
import com.metax.common.CommonResult;
import com.metax.tool.reference.ToolReferenceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ToolReferenceController .
 *
 * <p>
 * Spring AI 官方工具调用参考接口，按 Methods as Tools 和 Functions as Tools 的不同暴露方式拆分入口
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "工具调用参考", description = "Spring AI Tools 官方路径参考接口")
public class ToolReferenceController {

    private final ToolReferenceService toolReferenceService;

    /**
     * 声明式方法 runtime tools 演示
     *
     * @param prompt 用户提示词
     * @return 工具参考响应
     */
    @GetMapping(value = "/v1/tools/reference/method/declarative/runtime",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "声明式方法工具 runtime tools 演示", description = "使用 ChatClient.tools 暴露 @Tool 对象")
    public ResponseEntity<CommonResult<ToolReferenceResponse>> methodDeclarativeRuntimeTools(
            @Parameter(description = "用户提示词") @RequestParam(required = false) String prompt) {
        return ResponseEntity.ok(CommonResult.success(toolReferenceService.methodDeclarativeRuntimeTools(prompt)));
    }

    /**
     * 声明式方法 runtime callbacks 演示
     *
     * @param prompt 用户提示词
     * @return 工具参考响应
     */
    @GetMapping(value = "/v1/tools/reference/method/declarative/callbacks",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "声明式方法工具 runtime callbacks 演示", description = "使用 ToolCallbacks.from 暴露 @Tool 方法")
    public ResponseEntity<CommonResult<ToolReferenceResponse>> methodDeclarativeRuntimeCallbacks(
            @Parameter(description = "用户提示词") @RequestParam(required = false) String prompt) {
        return ResponseEntity.ok(CommonResult.success(toolReferenceService.methodDeclarativeRuntimeCallbacks(prompt)));
    }

    /**
     * 编程式方法 runtime callbacks 演示
     *
     * @param prompt 用户提示词
     * @return 工具参考响应
     */
    @GetMapping(value = "/v1/tools/reference/method/programmatic/runtime",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "编程式方法工具 runtime callbacks 演示", description = "使用 MethodToolCallback 暴露普通 Java 方法")
    public ResponseEntity<CommonResult<ToolReferenceResponse>> methodProgrammaticRuntimeCallbacks(
            @Parameter(description = "用户提示词") @RequestParam(required = false) String prompt) {
        return ResponseEntity.ok(CommonResult.success(toolReferenceService.methodProgrammaticRuntimeCallbacks(prompt)));
    }

    /**
     * 编程式方法 default callbacks 演示
     *
     * @param prompt 用户提示词
     * @return 工具参考响应
     */
    @GetMapping(value = "/v1/tools/reference/method/programmatic/default",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "编程式方法工具 default callbacks 演示", description = "使用 defaultToolCallbacks 暴露 MethodToolCallback")
    public ResponseEntity<CommonResult<ToolReferenceResponse>> methodProgrammaticDefaultCallbacks(
            @Parameter(description = "用户提示词") @RequestParam(required = false) String prompt) {
        return ResponseEntity.ok(CommonResult.success(toolReferenceService.methodProgrammaticDefaultCallbacks(prompt)));
    }

    /**
     * 编程式函数 runtime callbacks 演示
     *
     * @param prompt 用户提示词
     * @return 工具参考响应
     */
    @GetMapping(value = "/v1/tools/reference/function/programmatic/runtime",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "编程式函数工具 runtime callbacks 演示", description = "使用 FunctionToolCallback 暴露 Function 系列接口")
    public ResponseEntity<CommonResult<ToolReferenceResponse>> functionProgrammaticRuntimeCallbacks(
            @Parameter(description = "用户提示词") @RequestParam(required = false) String prompt) {
        return ResponseEntity.ok(CommonResult.success(toolReferenceService.functionProgrammaticRuntimeCallbacks(prompt)));
    }

    /**
     * 编程式函数 default callbacks 演示
     *
     * @param prompt 用户提示词
     * @return 工具参考响应
     */
    @GetMapping(value = "/v1/tools/reference/function/programmatic/default",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "编程式函数工具 default callbacks 演示", description = "使用 defaultToolCallbacks 暴露 FunctionToolCallback")
    public ResponseEntity<CommonResult<ToolReferenceResponse>> functionProgrammaticDefaultCallbacks(
            @Parameter(description = "用户提示词") @RequestParam(required = false) String prompt) {
        return ResponseEntity.ok(CommonResult.success(toolReferenceService.functionProgrammaticDefaultCallbacks(prompt)));
    }

    /**
     * Spring Bean 函数 resolver 演示
     *
     * @param prompt 用户提示词
     * @return 工具参考响应
     */
    @GetMapping(value = "/v1/tools/reference/function/bean/resolver",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Spring Bean 函数工具 resolver 演示", description = "使用 SpringBeanToolCallbackResolver 按 Bean 名解析 Function")
    public ResponseEntity<CommonResult<ToolReferenceResponse>> functionBeanResolverCallbacks(
            @Parameter(description = "用户提示词") @RequestParam(required = false) String prompt) {
        return ResponseEntity.ok(CommonResult.success(toolReferenceService.functionBeanResolverCallbacks(prompt)));
    }

    /**
     * 工具契约查看
     *
     * @return 工具参考响应
     */
    @GetMapping(value = "/v1/tools/reference/specification",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "工具契约查看", description = "返回 ToolDefinition、ToolMetadata 和 returnDirect 示例")
    public ResponseEntity<CommonResult<ToolReferenceResponse>> specification() {
        return ResponseEntity.ok(CommonResult.success(toolReferenceService.specification()));
    }

    /**
     * 工具执行选项查看
     *
     * @return 工具参考响应
     */
    @GetMapping(value = "/v1/tools/reference/execution",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "工具执行选项查看", description = "返回 ToolCallingChatOptions 和 ToolCallAdvisor 执行路径说明")
    public ResponseEntity<CommonResult<ToolReferenceResponse>> execution() {
        return ResponseEntity.ok(CommonResult.success(toolReferenceService.execution()));
    }

    /**
     * 工具定义查看
     *
     * @return 工具参考响应
     */
    @GetMapping(value = "/v1/tools/reference/definitions",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "工具定义查看", description = "返回方法工具和函数工具生成的 ToolDefinition")
    public ResponseEntity<CommonResult<ToolReferenceResponse>> definitions() {
        return ResponseEntity.ok(CommonResult.success(toolReferenceService.definitions()));
    }
}
