package com.metax.chat.history;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.metax.common.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

/**
 * MetaChatHistoryController .
 *
 * <p>
 * 完整聊天历史查询接口
 * ChatController 只负责对话聊天和历史写入，后续可独立支持流式返回
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "聊天历史", description = "完整聊天历史归档查询接口")
public class MetaChatHistoryController {

    private final MetaChatHistoryService metaChatHistoryService;

    /**
     * 查询完整聊天历史
     *
     * <p>
     * 这里查询的是 MetaChatHistory 完整归档，不是 ChatMemory 最近窗口
     * ChatMemory 只服务 prompt 上下文，超过 maxMessages 的旧消息会被裁剪
     *
     * @param request 查询参数
     * @return 历史消息分页
     */
    @GetMapping(value = "/v1/chat/history/page")
    @Operation(summary = "查询完整聊天历史", description = "按 conversationId 分页查询完整历史，不读取 ChatMemory 窗口记忆")
    public CommonResult<Page<MetaChatHistoryDO>> page(@Valid @ParameterObject MetaChatHistoryPageRequest request) {
        if (request.getChatId() != null) {
            return CommonResult.success(metaChatHistoryService.pageByChatId(request.getChatId(),
                    request.getCurrent(), request.getSize()));
        }
        if (!StringUtils.hasText(request.getConversationId())) {
            throw new IllegalArgumentException("conversationId 不能为空");
        }
        return CommonResult.success(metaChatHistoryService.pageByConversationId(request.getConversationId(),
                request.getCurrent(), request.getSize()));
    }
}
