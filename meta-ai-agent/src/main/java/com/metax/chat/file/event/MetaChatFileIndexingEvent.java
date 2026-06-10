package com.metax.chat.file.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * MetaChatFileIndexingEvent .
 *
 * <p>
 * 会话文件索引事件，用于在上传事务提交后触发后台解析和临时向量索引
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/10
 */
@Getter
public class MetaChatFileIndexingEvent extends ApplicationEvent {

    /**
     * 文件 ID
     */
    private final String fileId;

    /**
     * 构造会话文件索引事件
     *
     * @param source 事件源
     * @param fileId 文件 ID
     */
    public MetaChatFileIndexingEvent(Object source, String fileId) {
        super(source);
        this.fileId = fileId;
    }
}
