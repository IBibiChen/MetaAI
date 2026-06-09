package com.metax.chat.file;

import com.metax.common.CommonResult;
import com.metax.rag.retrieval.advisor.MetaContextFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
     *
     * @param request 会话文件上传请求
     * @return 已解析完成的会话文件
     */
    @PostMapping(value = "/v1/chat/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传会话文件", description = "上传当前会话临时文件，供后续普通聊天或知识库问答使用")
    public CommonResult<List<MetaContextFile>> upload(@Valid @ModelAttribute MetaChatFileUploadRequest request) {
        return CommonResult.success(metaChatFileService.uploadAndIndex(request.getTenantId(), request.getUserId(),
                request.getChatId(), request.getFiles()));
    }

    /**
     * 查询会话可用文件
     *
     * <p>
     * 只返回 READY 文件，供前端展示当前会话可参与上下文增强的文件集合
     *
     * @param request 会话文件查询请求
     * @return 当前会话已解析完成的文件
     */
    @GetMapping(value = "/v1/chat/files")
    @Operation(summary = "查询会话可用文件", description = "查询当前会话已解析完成、可参与文件上下文的临时文件")
    public CommonResult<List<MetaContextFile>> readyFiles(@Valid @ParameterObject MetaChatFileQueryRequest request) {
        return CommonResult.success(metaChatFileService.readyFiles(request.getTenantId(), request.getUserId(),
                request.getChatId()));
    }

    /**
     * 会话文件查询请求
     *
     * <p>
     * tenantId、userId 和 chatId 共同限定会话文件隔离边界
     */
    @Setter
    @Getter
    @Schema(description = "会话文件查询请求")
    public static class MetaChatFileQueryRequest {

        /**
         * 会话 ID
         */
        @Parameter(description = "会话 ID", example = "t1:u1:s1")
        @Schema(description = "会话 ID", example = "t1:u1:s1")
        @NotBlank(message = "chatId 不能为空")
        private String chatId;

        /**
         * 租户 ID
         */
        @Parameter(description = "租户 ID", example = "t1")
        @Schema(description = "租户 ID", example = "t1")
        @NotBlank(message = "tenantId 不能为空")
        private String tenantId;

        /**
         * 用户 ID
         */
        @Parameter(description = "用户 ID", example = "u1")
        @Schema(description = "用户 ID", example = "u1")
        @NotBlank(message = "userId 不能为空")
        private String userId;

    }

    /**
     * 会话文件上传请求
     *
     * <p>
     * 仅文件上传接口保留 MultipartFile，聊天和 RAG 问答统一使用 fileIds
     */
    @Setter
    @Getter
    @Schema(description = "会话文件上传请求")
    public static class MetaChatFileUploadRequest extends MetaChatFileQueryRequest {

        /**
         * 聊天文件
         */
        @Parameter(description = "聊天文件")
        @ArraySchema(schema = @Schema(description = "聊天文件", type = "string", format = "binary"))
        private MultipartFile[] files;

    }

}
