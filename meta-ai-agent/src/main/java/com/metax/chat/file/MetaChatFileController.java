package com.metax.chat.file;

import com.metax.common.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MetaChatFileController .
 *
 * <p>
 * 会话级临时文件上传和查询接口
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/9
 */
@Validated
@RestController
@RequiredArgsConstructor
@Tag(name = "会话文件", description = "会话级临时文件上传、解析和查询接口")
public class MetaChatFileController {

    private final MetaChatFileService metaChatFileService;

    /**
     * 上传会话文件
     *
     * <p>
     * 文件只绑定当前 chatId，写入 scope = session 的临时向量索引，不进入知识库
     * 问答接口不再接收 MultipartFile，只通过本接口返回的 fileId 引用文件
     * 上传接口快速返回文件状态，OCR、切分和向量化由后台任务继续处理
     *
     * @param request 会话文件上传请求
     * @return 已提交处理的会话文件
     */
    @PostMapping(value = "/v1/chat/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传会话文件", description = "上传当前会话临时文件，供后续普通聊天或知识库问答使用")
    public CommonResult<List<MetaChatFileItemResponse>> upload(@Valid @ModelAttribute MetaChatFileUploadRequest request) {
        return CommonResult.success(metaChatFileService.uploadAndSubmitIndex(request.getTenantId(), request.getUserId(),
                request.getChatId(), request.getFiles()));
    }

    /**
     * 查询会话文件状态
     *
     * <p>
     * 返回当前会话全部未删除文件，供前端展示解析中、已就绪和解析失败状态
     * 问答链路仍通过 readyFiles 只使用 READY 文件
     *
     * @param request 会话文件查询请求
     * @return 当前会话文件状态列表
     */
    @GetMapping(value = "/v1/chat/files")
    @Operation(summary = "查询会话文件状态", description = "查询当前会话临时文件状态，供前端轮询展示")
    public CommonResult<List<MetaChatFileItemResponse>> files(@Valid @ParameterObject MetaChatFileQueryRequest request) {
        return CommonResult.success(metaChatFileService.listFiles(request.getTenantId(), request.getUserId(),
                request.getChatId()));
    }

}
