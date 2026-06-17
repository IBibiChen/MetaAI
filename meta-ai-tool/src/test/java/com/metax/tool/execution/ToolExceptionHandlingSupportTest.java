package com.metax.tool.execution;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ToolExceptionHandlingSupportTest .
 *
 * <p>
 * 工具异常处理策略单元测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/17
 */
class ToolExceptionHandlingSupportTest {

    @Test
    void modelFeedbackExceptionProcessorShouldReturnExceptionMessage() {
        ToolExecutionExceptionProcessor processor = ToolExceptionHandlingSupport.modelFeedbackExceptionProcessor();

        String result = processor.process(toolExecutionException(new IllegalArgumentException("invalid zone")));

        assertThat(result).isEqualTo("invalid zone");
    }

    @Test
    void throwingExceptionProcessorShouldThrowToolExecutionException() {
        ToolExecutionExceptionProcessor processor = ToolExceptionHandlingSupport.throwingExceptionProcessor();
        ToolExecutionException exception = toolExecutionException(new IllegalArgumentException("invalid zone"));

        assertThatThrownBy(() -> processor.process(exception))
                .isSameAs(exception);
    }

    @Test
    void rethrowingExceptionProcessorShouldUnwrapConfiguredRuntimeException() {
        ToolExecutionExceptionProcessor processor = ToolExceptionHandlingSupport.rethrowingExceptionProcessor(
                List.of(IllegalArgumentException.class));

        assertThatThrownBy(() -> processor.process(toolExecutionException(new IllegalArgumentException("invalid zone"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid zone");
    }

    private ToolExecutionException toolExecutionException(RuntimeException cause) {
        ToolDefinition definition = ToolDefinition.builder()
                .name("testTool")
                .description("Test tool")
                .inputSchema("{}")
                .build();
        return new ToolExecutionException(definition, cause);
    }
}
