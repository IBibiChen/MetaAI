package com.metax.external.adapter.sync;

import com.metax.external.adapter.config.ExternalAdapterProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * ExternalAdapterSyncWorker .
 *
 * <p>
 * 第三方系统适配器文档学习串行 Worker
 *
 * <p>
 * 该 Worker 只启动一个后台线程，从数据库持久队列中一次抢占一条任务
 * 每条任务必须完整走完下载、对象存储归档、索引提交和索引终态等待，才会继续处理下一条任务
 * 这样可以把 OCR、PDF 解析和向量化这类重型资源消耗控制在单机单任务
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/12
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "metax.external-adapter.enabled", havingValue = "true")
public class ExternalAdapterSyncWorker implements SmartLifecycle {

    /**
     * Spring 生命周期阶段
     *
     * <p>
     * phase 越大启动越晚、停止越早
     * Worker 依赖数据库、WebClient 等基础设施，所以需要尽量晚启动，并在应用关闭时尽早停止拉取新任务
     */
    private static final int WORKER_PHASE = Integer.MAX_VALUE - 100;

    private final ExternalAdapterProperties properties;

    private final ExternalDocumentSyncService syncService;

    /**
     * Worker 运行标志
     *
     * <p>
     * start / stop 可能由 Spring 生命周期线程调用，runWorkerLoop 在工作线程中读取
     * volatile 保证停止信号能被工作线程及时看见
     */
    private volatile boolean running;

    /**
     * 唯一工作线程
     *
     * <p>
     * 该线程负责串行消费数据库队列
     * 保持单线程是为了限制 OCR、PDF 解析和向量化在单机上的资源占用
     */
    private Thread workerThread;

    /**
     * Spring 异步停止回调
     *
     * <p>
     * stop(Runnable) 由 Spring 容器关闭时调用
     * Worker 真正退出后必须执行该回调，Spring 才会认为当前生命周期组件已经停止
     */
    private Runnable stopCallback;

    /**
     * 启动单线程 Worker
     *
     * <p>
     * SmartLifecycle 会在 Spring 容器启动完成后调用该方法
     * 这里显式创建一个普通后台工作线程，而不是 daemon 线程，避免 JVM 退出时直接切断正在处理的文件
     */
    @Override
    public synchronized void start() {
        if (running || !properties.getWorker().isEnabled()) {
            return;
        }
        // running 先置为 true，再启动线程，确保 runWorkerLoop 进入循环后能正常拉取任务
        running = true;
        // this::runWorkerLoop 表示把 Worker 主循环作为线程入口
        // external-adapter-sync-worker 是线程名，方便从日志、线程 dump 和监控中定位该后台任务
        workerThread = new Thread(this::runWorkerLoop, "external-adapter-sync-worker");
        workerThread.start();
        log.info("第三方系统适配器同步 Worker 已启动：idleInterval = {}，indexTimeout = {}",
                properties.getWorker().getIdleInterval(), properties.getWorker().getIndexTimeout());
    }

    /**
     * 请求停止 Worker
     *
     * <p>
     * 手动调用 stop 时只负责发出停止信号并中断休眠
     * 正在执行的任务如果已经进入索引等待，会通过线程中断和数据库锁超时机制恢复
     */
    @Override
    public void stop() {
        requestStop(null);
        waitForWorkerExit();
    }

    /**
     * 请求停止 Worker 并在退出后回调 Spring
     *
     * <p>
     * Spring 关闭容器时优先调用该方法
     * callback 必须在 Worker 线程退出后执行，否则容器会一直等待当前生命周期阶段结束
     *
     * @param callback Spring 生命周期停止回调
     */
    @Override
    public void stop(@NonNull Runnable callback) {
        requestStop(callback);
    }

    /**
     * 发出停止信号
     *
     * @param callback Worker 退出后的回调，可为空
     */
    private synchronized void requestStop(Runnable callback) {
        // 先关闭运行标志，阻止主循环继续拉取新任务
        running = false;
        stopCallback = callback;
        if (workerThread != null) {
            // interrupt 用于打断空闲 sleep 或索引等待 sleep，让应用关闭不必等完整轮询间隔
            workerThread.interrupt();
            return;
        }
        runStopCallback();
    }

    /**
     * 等待 Worker 线程退出
     *
     * <p>
     * 非 Spring 回调场景下给 Worker 一个短暂退出窗口
     * 如果线程正在执行不可中断的外部调用，数据库锁超时会保证任务后续可重新接管
     */
    private void waitForWorkerExit() {
        Thread thread = workerThread;
        if (thread == null) {
            return;
        }
        try {
            thread.join(5_000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Worker 是否运行中
     *
     * @return true 表示运行中
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 是否随 Spring 容器自动启动
     *
     * @return true 表示容器启动时自动启动
     */
    @Override
    public boolean isAutoStartup() {
        return true;
    }

    /**
     * 生命周期阶段
     *
     * <p>
     * 使用较大的 phase，让 Worker 尽量在数据库、WebClient 等基础设施准备完成后启动
     * 关闭时则会较早收到停止信号，减少应用退出时继续拉取新任务的概率
     *
     * @return 生命周期阶段
     */
    @Override
    public int getPhase() {
        return WORKER_PHASE;
    }

    /**
     * Worker 主循环
     *
     * <p>
     * 这个循环永远只在一个线程中运行
     * pollAndProcessOneTask 内部会等待当前文件索引进入终态，因此循环天然保证同一时间只有一个重型任务在执行
     */
    private void runWorkerLoop() {
        try {
            while (running) {
                try {
                    // 每轮最多处理一条任务，pollAndProcessOneTask 内部会阻塞等待该任务进入终态或重试状态
                    boolean processed = syncService.pollAndProcessOneTask();
                    if (!processed) {
                        // 队列为空时必须休眠，避免 while 循环空转导致单机 CPU 飙高
                        sleepBeforeNextPoll(properties.getWorker().getIdleInterval());
                    }
                } catch (RuntimeException ex) {
                    log.warn("第三方系统适配器同步 Worker 本轮执行失败：error = {}", ex.getMessage(), ex);
                    // 单轮异常后短暂休眠，避免外部服务故障时快速重复打爆日志和数据库
                    sleepBeforeNextPoll(properties.getWorker().getIdleInterval());
                }
            }
        } finally {
            markStopped();
        }
    }

    /**
     * Worker 空闲等待
     *
     * @param duration 等待下一次拉取任务的时长
     */
    private void sleepBeforeNextPoll(java.time.Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            // 收到中断说明外部请求停止 Worker，恢复中断标记并退出主循环
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    /**
     * 标记 Worker 已停止
     */
    private synchronized void markStopped() {
        // 工作线程已经退出，清空线程引用，避免后续 stop 误以为仍有线程需要中断
        running = false;
        workerThread = null;
        log.info("第三方系统适配器同步 Worker 已停止");
        runStopCallback();
    }

    /**
     * 执行 Spring 停止回调
     */
    private void runStopCallback() {
        if (stopCallback == null) {
            return;
        }
        // callback 只允许执行一次，先取出再清空，避免重复 stop 时重复通知 Spring
        Runnable callback = stopCallback;
        stopCallback = null;
        callback.run();
    }
}
