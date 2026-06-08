package com.metax.retrieval.indexing.request;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DocumentImportRequest .
 *
 * <p>
 * 对象存储文档索引导入请求参数
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@Data
@Schema(description = "对象存储文档索引导入请求参数")
public class DocumentImportRequest {

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
     * 文档 ID
     */
    @NotBlank(message = "documentId 不能为空")
    @Parameter(description = "文档 ID，同一 documentId 重复导入会覆盖旧 chunk", example = "doc-001",
            required = true)
    @Schema(description = "文档 ID，同一 documentId 重复导入会覆盖旧 chunk", example = "doc-001")
    private String documentId;

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
    @Parameter(description = "文档类型，可为空，为空时根据 objectKey 后缀识别", example = "md")
    @Schema(description = "文档类型，可为空，为空时根据 objectKey 后缀识别", example = "md")
    private String documentType;

    /**
     * 来源标识
     */
    @Parameter(description = "来源标识，可用于前端展示引用来源", example = "knowledge/t1/kb1/demo.md")
    @Schema(description = "来源标识，可用于前端展示引用来源", example = "knowledge/t1/kb1/demo.md")
    private String source;

    /**
     * 对象存储 bucket
     */
    @NotBlank(message = "bucket 不能为空")
    @Parameter(description = "对象存储 bucket", example = "meta-ai-knowledge", required = true)
    @Schema(description = "对象存储 bucket", example = "meta-ai-knowledge")
    private String bucket;

    /**
     * 对象存储 object key
     */
    @NotBlank(message = "objectKey 不能为空")
    @Parameter(description = "对象存储 object key", example = "knowledge/t1/kb1/demo.md", required = true)
    @Schema(description = "对象存储 object key", example = "knowledge/t1/kb1/demo.md")
    private String objectKey;
}
