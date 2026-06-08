package com.metax.storage;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.metax.common.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * StorageDocumentController .
 *
 * <p>
 * 对象存储文档上传、查询、下载和索引接口
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
@RestController
@Validated
@RequiredArgsConstructor
@Tag(name = "对象存储文档", description = "对象存储文档上传、查询、下载和索引接口")
public class StorageDocumentController {

    private final StorageDocumentService storageDocumentService;

    /**
     * 上传对象存储文档
     *
     * @param tenantId        租户 ID
     * @param kbId 知识库 ID
     * @param visibility      文档可见性
     * @param deptId          部门 ID
     * @param userId          用户 ID
     * @param documentType    文档类型
     * @param autoIndex       是否上传后自动索引
     * @param file            上传文件
     * @return 上传响应
     */
    @PostMapping(value = "/v1/storage/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传对象存储文档", description = "上传文件到 RustFS，并保存对象存储文档元数据")
    public CommonResult<StorageDocumentUploadResponse> upload(
            @Parameter(description = "租户 ID", example = "t1", required = true)
            @NotBlank(message = "tenantId 不能为空") @RequestParam(name = "tenantId") String tenantId,
            @Parameter(description = "知识库 ID", example = "kb1", required = true)
            @NotBlank(message = "kbId 不能为空")
            @RequestParam(name = "kbId") String kbId,
            @Parameter(description = "文档可见性，可选值：PUBLIC、DEPT、USER", example = "PUBLIC")
            @RequestParam(name = "visibility", defaultValue = "PUBLIC") String visibility,
            @Parameter(description = "部门 ID，visibility=DEPT 时必填", example = "d1")
            @RequestParam(name = "deptId", required = false) String deptId,
            @Parameter(description = "用户 ID，visibility=USER 时必填", example = "u1")
            @RequestParam(name = "userId", required = false) String userId,
            @Parameter(description = "文档类型，可为空，为空时索引阶段根据文件名识别", example = "pdf")
            @RequestParam(name = "documentType", required = false) String documentType,
            @Parameter(description = "是否上传后自动提交索引执行", example = "false")
            @RequestParam(name = "autoIndex", defaultValue = "false") Boolean autoIndex,
            @Parameter(description = "上传文件", required = true)
            @NotNull(message = "file 不能为空") @RequestPart(name = "file") MultipartFile file) {
        return CommonResult.success(storageDocumentService.upload(tenantId, kbId, visibility, deptId,
                userId, documentType, autoIndex, file));
    }

    /**
     * 分页查询对象存储文档
     *
     * @param request 查询参数
     * @return 分页结果
     */
    @GetMapping(value = "/v1/storage/documents/page")
    @Operation(summary = "分页查询对象存储文档", description = "按租户、知识库、索引状态和文件名关键字分页查询文档元数据")
    public CommonResult<Page<StorageDocumentDO>> page(@Valid @ParameterObject StorageDocumentPageRequest request) {
        return CommonResult.success(storageDocumentService.pageDocuments(request.getTenantId(),
                request.getKbId(), request.getVisibility(), request.getDeptId(), request.getUserId(),
                request.getIndexStatus(), request.getKeyword(), request.getCurrent(), request.getSize()));
    }

    /**
     * 下载对象存储文档
     *
     * @param request    范围参数
     * @param documentId 文档 ID
     * @return 文件流响应
     */
    @GetMapping(value = "/v1/storage/documents/{documentId}/download")
    @Operation(summary = "下载对象存储文档", description = "按 documentId 从 RustFS 下载对象存储文档",
            responses = {
                    @ApiResponse(responseCode = "200", description = "文件流响应",
                            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                                    schema = @Schema(type = "string", format = "binary")))
            })
    public ResponseEntity<InputStreamResource> download(
            @Valid @ParameterObject StorageDocumentScopeRequest request,
            @Parameter(description = "文档 ID", example = "1938200000000000001", required = true,
                    in = ParameterIn.PATH)
            @NotBlank(message = "documentId 不能为空") @PathVariable String documentId) {
        StorageDocumentDownload download = storageDocumentService.download(request.getTenantId(),
                request.getKbId(), documentId);
        return downloadResponse(download);
    }

    /**
     * 按全局 documentId 下载对象存储文档
     *
     * <p>
     * 普通知识库问答响应 references 只返回 documentId，前端点击文件名时调用该接口下载原始文件
     *
     * @param documentId 文档 ID
     * @return 文件流响应
     */
    @GetMapping(value = "/v1/storage/documents/download/{documentId}")
    @Operation(summary = "按 documentId 下载对象存储文档", description = "根据全局 documentId 从 RustFS 下载对象存储文档",
            responses = {
                    @ApiResponse(responseCode = "200", description = "文件流响应",
                            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                                    schema = @Schema(type = "string", format = "binary")))
            })
    public ResponseEntity<InputStreamResource> downloadByDocumentId(
            @Parameter(description = "文档 ID", example = "1938200000000000001", required = true,
                    in = ParameterIn.PATH)
            @NotBlank(message = "documentId 不能为空") @PathVariable String documentId) {
        return downloadResponse(storageDocumentService.download(documentId));
    }

    /**
     * 构造文件流响应
     *
     * <p>
     * Content-Disposition 使用 UTF-8 文件名，避免中文文件名下载时乱码
     *
     * @param download 下载结果
     * @return 文件流响应
     */
    private ResponseEntity<InputStreamResource> downloadResponse(StorageDocumentDownload download) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build();
        MediaType contentType = StringUtils.hasText(download.contentType())
                ? MediaType.parseMediaType(download.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(contentType)
                .contentLength(download.fileSize())
                .body(new InputStreamResource(download.inputStream()));
    }

    /**
     * 提交对象存储文档索引执行
     *
     * @param request    范围参数
     * @param documentId 文档 ID
     * @return 文档元数据
     */
    @PostMapping(value = "/v1/storage/documents/{documentId}/index")
    @Operation(summary = "提交对象存储文档索引执行", description = "根据 documentId 读取对象存储元数据并提交异步索引执行")
    public CommonResult<StorageDocumentDO> index(
            @Valid @ParameterObject StorageDocumentScopeRequest request,
            @Parameter(description = "文档 ID", example = "1938200000000000001", required = true,
                    in = ParameterIn.PATH)
            @NotBlank(message = "documentId 不能为空") @PathVariable String documentId) {
        return CommonResult.success(storageDocumentService.index(request.getTenantId(), request.getKbId(),
                documentId));
    }
}
