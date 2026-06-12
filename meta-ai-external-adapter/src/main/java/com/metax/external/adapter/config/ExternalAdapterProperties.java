package com.metax.external.adapter.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ExternalAdapterProperties .
 *
 * <p>
 * 第三方系统文档同步适配器配置
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/11
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "metax.external-adapter")
public class ExternalAdapterProperties {

    /**
     * 是否启用第三方系统文档同步适配器
     */
    private boolean enabled = false;

    /**
     * 固定同步目标租户 ID
     */
    private String tenantId = "t1";

    /**
     * 固定同步目标知识库 ID
     */
    private String kbId = "kb1";

    /**
     * 最大重试次数
     */
    private int maxAttempts = 3;

    /**
     * 后台串行 Worker 配置
     */
    private Worker worker = new Worker();

    /**
     * 兜底补偿扫描配置
     */
    private Reconcile reconcile = new Reconcile();

    /**
     * 外部文件服务下载配置
     */
    private FileService fileService = new FileService();

    /**
     * 调用本应用对象存储文档接口配置
     */
    private StorageApi storageApi = new StorageApi();

    @Getter
    @Setter
    public static class Reconcile {

        /**
         * 是否启用兜底补偿扫描
         */
        private boolean enabled = true;

        /**
         * 单次扫描批量大小
         */
        private int batchSize = 50;

        /**
         * 扫描间隔
         */
        private long fixedDelayMillis = 300_000L;
    }

    @Getter
    @Setter
    public static class Worker {

        /**
         * 是否启用后台串行 Worker
         */
        private boolean enabled = true;

        /**
         * 队列没有可处理任务时，Worker 休眠多久再拉取下一轮
         */
        private java.time.Duration idleInterval = java.time.Duration.ofSeconds(5);

        /**
         * 任务锁超时时间
         *
         * <p>
         * 应用异常退出或 Worker 被强制中断后，超过该时间的运行中任务允许被后续 Worker 重新接管
         */
        private java.time.Duration lockTimeout = java.time.Duration.ofMinutes(30);

        /**
         * 索引状态初始轮询间隔
         *
         * <p>
         * 单个文件提交索引后，第一次等待多久再查询索引状态
         */
        private java.time.Duration indexPollInitialInterval = java.time.Duration.ofSeconds(2);

        /**
         * 索引状态最大轮询间隔
         *
         * <p>
         * 指数退避后的等待间隔不会超过该值
         */
        private java.time.Duration indexPollMaxInterval = java.time.Duration.ofSeconds(10);

        /**
         * 索引状态轮询退避倍率
         *
         * <p>
         * 每次未进入终态时，将下一轮等待时间按该倍率放大
         */
        private double indexPollMultiplier = 1.5D;

        /**
         * 单个文件等待索引终态的最大时间
         *
         * <p>
         * 超过该时间仍未进入 INDEXED / INDEX_FAILED 时，当前任务会进入重试或失败，Worker 继续处理下一条任务
         */
        private java.time.Duration indexTimeout = java.time.Duration.ofMinutes(30);
    }

    @Getter
    @Setter
    public static class FileService {

        /**
         * 外部文件服务地址
         */
        private String host = "http://localhost:10086";

        /**
         * 外部文件服务下载路径前缀
         */
        private String downloadUrl = "/v1/download/";

        /**
         * 外部文件服务 Authorization Header
         */
        private String authorization = "";

        /**
         * 外部文件服务下载超时时间
         */
        private java.time.Duration timeout = java.time.Duration.ofMinutes(5);
    }

    @Getter
    @Setter
    public static class StorageApi {

        /**
         * 本应用对象存储文档接口地址
         */
        private String baseUrl = "http://127.0.0.1:8008";

        /**
         * 上传后是否自动提交索引
         */
        private boolean autoIndex = true;

        /**
         * 对象存储文档上传超时时间
         */
        private java.time.Duration uploadTimeout = java.time.Duration.ofMinutes(10);
    }
}
