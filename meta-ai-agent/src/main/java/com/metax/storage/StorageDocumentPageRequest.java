package com.metax.storage;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * StorageDocumentPageRequest .
 *
 * <p>
 * 对象存储文档分页查询参数
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
@Data
@Schema(description = "对象存储文档分页查询参数")
public class StorageDocumentPageRequest {

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
    @Parameter(description = "文档可见性", example = "PUBLIC")
    @Schema(description = "文档可见性", example = "PUBLIC")
    private String visibility;

    /**
     * 部门 ID
     */
    @Parameter(description = "部门 ID", example = "d1")
    @Schema(description = "部门 ID", example = "d1")
    private String deptId;

    /**
     * 用户 ID
     */
    @Parameter(description = "用户 ID", example = "u1")
    @Schema(description = "用户 ID", example = "u1")
    private String userId;

    /**
     * 索引状态
     */
    @Parameter(description = "索引状态", example = "INDEXED")
    @Schema(description = "索引状态", example = "INDEXED")
    private String indexStatus;

    /**
     * 文件名关键字
     */
    @Parameter(description = "文件名关键字", example = "demo")
    @Schema(description = "文件名关键字", example = "demo")
    private String keyword;

    /**
     * 页码，从 1 开始
     */
    @Min(value = 1, message = "current 不能小于 1")
    @Parameter(description = "页码，从 1 开始", example = "1")
    @Schema(description = "页码，从 1 开始", example = "1")
    private Long current;

    /**
     * 每页数量
     */
    @Min(value = 1, message = "size 不能小于 1")
    @Max(value = 500, message = "size 不能大于 500")
    @Parameter(description = "每页数量", example = "20")
    @Schema(description = "每页数量", example = "20")
    private Long size;
}
