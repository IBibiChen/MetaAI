package com.metax.external.adapter.source;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * ExternalSourceFileDO .
 *
 * <p>
 * 第三方系统资料库文件实体
 * 该表来自第三方业务系统，只由 MyBatis Plus 读写必要字段，不交给 JPA 管理表结构
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/12
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pikb_file_info")
public class ExternalSourceFileDO {

    /**
     * 第三方系统文件 ID
     */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /**
     * 第三方文件路径
     *
     * <p>
     * 适配器会基于该路径调用第三方文件服务下载原始文件流
     */
    @TableField("file_path")
    private String filePath;

    /**
     * 文件名
     */
    @TableField("file_name")
    private String fileName;

    /**
     * 文件类型
     */
    @TableField("file_type")
    private String fileType;

    /**
     * 文件大小
     */
    @TableField("file_size")
    private String fileSize;

    /**
     * 文件哈希
     *
     * <p>
     * 用于判断第三方文件内容是否变化，变化后需要重新进入学习链路
     */
    @TableField("hash_code")
    private String hashCode;

    /**
     * 资料库类型
     *
     * <p>
     * 第三方系统约定该字段有值时表示文件需要学习
     * 适配器只同步 libraryType 有值的文件
     */
    @TableField("library_type")
    private String libraryType;

    /**
     * 删除标识
     *
     * <p>
     * 只处理 isDelete = 0 的文件，已删除文件不会进入学习队列
     */
    @TableField("is_delete")
    private Integer isDelete;

    /**
     * AI 文件学习状态
     *
     * <p>
     * 第三方系统状态码：0 表示推送成功、学习中，1 表示推送失败，2 表示学习完成，3 表示学习失败
     * 该字段用于给第三方系统展示学习进度，不等同于适配器内部 syncStatus
     */
    @TableField("status")
    private Integer status;

    /**
     * 本系统文档 ID
     *
     * <p>
     * 保存 MetaAI 侧生成的 documentId，用于建立第三方文件和本系统对象存储文档的关联
     */
    @TableField("chat_file_id")
    private String chatFileId;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private Instant updateTime;
}
