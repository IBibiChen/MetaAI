package com.metax.external.adapter.storage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ExternalStorageDocumentDO .
 *
 * <p>
 * 对象存储文档索引状态只读实体
 * 适配器只关心 documentId、indexStatus 和 deleted，不重复接入 agent 模块服务
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/12
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("meta_storage_document")
public class ExternalStorageDocumentDO {

    /**
     * 文档元数据 ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 文档 ID
     *
     * <p>
     * 对应适配器同步表中的 documentId，用于查询对象存储文档的索引终态
     */
    @TableField("document_id")
    private String documentId;

    /**
     * 索引状态
     *
     * <p>
     * 来自 meta_storage_document.index_status
     * 适配器主要关心 INDEXED 和 INDEX_FAILED，分别表示向量化完成和向量化失败
     */
    @TableField("index_status")
    private String indexStatus;

    /**
     * 是否删除
     *
     * <p>
     * 只读取 deleted = false 的文档，避免软删除历史记录干扰学习状态回写
     */
    @TableField("deleted")
    private Boolean deleted;
}
