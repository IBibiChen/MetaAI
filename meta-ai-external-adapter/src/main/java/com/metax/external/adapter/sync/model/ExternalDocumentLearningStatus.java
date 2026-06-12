package com.metax.external.adapter.sync.model;

/**
 * ExternalDocumentLearningStatus .
 *
 * <p>
 * 第三方系统 AI 文件学习状态映射
 *
 * <p>
 * 该枚举用于回写第三方源表 status 字段
 * 它面向第三方系统展示结果，不参与适配器内部 Worker 抢占和重试判断
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/11
 */
public enum ExternalDocumentLearningStatus {

    /**
     * 推送成功，学习中
     */
    LEARNING(0),

    /**
     * 推送失败
     */
    PUSH_FAILED(1),

    /**
     * 学习完成
     */
    COMPLETED(2),

    /**
     * 学习失败
     */
    FAILED(3);

    /**
     * 第三方源表 status 字段状态码
     */
    private final int code;

    ExternalDocumentLearningStatus(int code) {
        this.code = code;
    }

    /**
     * 返回第三方系统状态码
     *
     * @return 第三方系统状态码
     */
    public int code() {
        return code;
    }
}
