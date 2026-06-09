package com.metax.storage.request;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * StorageDocumentScopeRequest .
 *
 * <p>
 * 对象存储文档租户和知识库范围参数
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/3
 */
@Data
@Schema(description = "对象存储文档租户和知识库范围参数")
public class StorageDocumentScopeRequest {

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
}
