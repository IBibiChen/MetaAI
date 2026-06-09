package com.metax.storage.request;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * StorageDocumentUploadRequest .
 *
 * <p>
 * 对象存储文档上传参数
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
@Data
@Schema(description = "对象存储文档上传参数")
public class StorageDocumentUploadRequest {

    /**
     * 租户 ID
     */
    @NotBlank(message = "tenantId 不能为空")
    @Parameter(description = "租户 ID", example = "t1", required = true)
    @Schema(description = "租户 ID", example = "t1")
    private String tenantId;

    /**
     * 知识库 ID
     */
    @NotBlank(message = "kbId 不能为空")
    @Parameter(description = "知识库 ID", example = "kb1", required = true)
    @Schema(description = "知识库 ID", example = "kb1")
    private String kbId;

    /**
     * 文档可见性
     */
    @Parameter(description = "文档可见性，可选值：PUBLIC、DEPT、USER", example = "PUBLIC")
    @Schema(description = "文档可见性，可选值：PUBLIC、DEPT、USER", example = "PUBLIC")
    private String visibility;

    /**
     * 部门 ID
     */
    @Parameter(description = "部门 ID，visibility=DEPT 时必填", example = "d1")
    @Schema(description = "部门 ID，visibility=DEPT 时必填", example = "d1")
    private String deptId;

    /**
     * 用户 ID
     */
    @Parameter(description = "用户 ID，visibility=USER 时必填", example = "u1")
    @Schema(description = "用户 ID，visibility=USER 时必填", example = "u1")
    private String userId;

    /**
     * 文档类型
     */
    @Parameter(description = "文档类型，可为空，为空时索引阶段根据文件名识别", example = "pdf")
    @Schema(description = "文档类型，可为空，为空时索引阶段根据文件名识别", example = "pdf")
    private String documentType;

    /**
     * 是否上传后自动索引
     */
    @Parameter(description = "是否上传后自动提交索引执行", example = "false")
    @Schema(description = "是否上传后自动提交索引执行", example = "false")
    private Boolean autoIndex;

    /**
     * 上传文件
     */
    @NotNull(message = "file 不能为空")
    @Parameter(description = "上传文件", required = true)
    @Schema(description = "上传文件", type = "string", format = "binary")
    private MultipartFile file;
}
