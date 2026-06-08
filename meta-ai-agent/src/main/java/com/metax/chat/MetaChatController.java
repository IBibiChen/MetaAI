package com.metax.chat;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.metax.common.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * MetaChatController .
 *
 * <p>
 * 聊天会话列表、状态和软删除接口
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "聊天会话", description = "聊天会话列表、重命名、置顶、收藏、归档和软删除接口")
public class MetaChatController {

    private final MetaChatService metaChatService;

    /**
     * 分页查询聊天会话
     *
     * @param request 查询参数
     * @return 会话分页
     */
    @GetMapping(value = "/v1/chats/page")
    @Operation(summary = "分页查询聊天会话", description = "按租户和用户查询会话列表，默认过滤软删除会话")
    public CommonResult<Page<MetaChatDO>> page(@Valid @ParameterObject MetaChatPageRequest request) {
        return CommonResult.success(metaChatService.pageChats(request));
    }

    /**
     * 查询聊天会话
     *
     * @param id 会话主键
     * @return 会话实体
     */
    @GetMapping(value = "/v1/chats/{id}")
    @Operation(summary = "查询聊天会话", description = "按会话主键查询聊天会话")
    public CommonResult<MetaChatDO> get(@PathVariable Long id) {
        MetaChatDO chat = metaChatService.getById(id);
        if (chat == null || Boolean.TRUE.equals(chat.getDeleted())) {
            throw new IllegalArgumentException("会话不存在");
        }
        return CommonResult.success(chat);
    }

    /**
     * 重命名聊天会话
     *
     * @param id      会话主键
     * @param request 重命名请求
     * @return 会话实体
     */
    @PatchMapping(value = "/v1/chats/{id}/title")
    @Operation(summary = "重命名聊天会话", description = "修改会话标题，后续消息不会覆盖用户编辑标题")
    public CommonResult<MetaChatDO> rename(@PathVariable Long id,
                                           @Valid @RequestBody MetaChatRenameRequest request) {
        return CommonResult.success(metaChatService.rename(id, request.getTitle()));
    }

    /**
     * 更新聊天会话状态
     *
     * @param id      会话主键
     * @param request 状态更新请求
     * @return 会话实体
     */
    @PatchMapping(value = "/v1/chats/{id}/flags")
    @Operation(summary = "更新聊天会话状态", description = "更新置顶、收藏和归档状态")
    public CommonResult<MetaChatDO> updateFlags(@PathVariable Long id,
                                                @RequestBody MetaChatFlagsRequest request) {
        return CommonResult.success(metaChatService.updateFlags(id, request));
    }

    /**
     * 软删除聊天会话
     *
     * @param id 会话主键
     * @return 成功响应
     */
    @DeleteMapping(value = "/v1/chats/{id}")
    @Operation(summary = "软删除聊天会话", description = "只软删除会话主表，不删除完整历史消息")
    public CommonResult<Void> delete(@PathVariable Long id) {
        metaChatService.softDelete(id);
        return CommonResult.success();
    }
}
