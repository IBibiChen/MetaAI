package com.metax.external.adapter.sync;

import java.time.Duration;

/**
 * IndexPollingBackoff .
 *
 * <p>
 * 索引状态轮询退避器
 *
 * <p>
 * 第一次轮询等待 initialInterval
 * 后续每次按 multiplier 放大等待时间，但不会超过 maxInterval
 * 这样既能在索引很快完成时及时感知，也能在 OCR / ETL 长时间运行时降低数据库查询频率
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/12
 */
public class IndexPollingBackoff {

    private final Duration maxInterval;

    private final double multiplier;

    private Duration currentInterval;

    /**
     * 创建指数退避器
     *
     * @param initialInterval 初始轮询间隔
     * @param maxInterval     最大轮询间隔
     * @param multiplier      退避倍率
     */
    public IndexPollingBackoff(Duration initialInterval, Duration maxInterval, double multiplier) {
        if (initialInterval == null || initialInterval.isNegative() || initialInterval.isZero()) {
            throw new IllegalArgumentException("初始轮询间隔必须大于 0");
        }
        if (maxInterval == null || maxInterval.compareTo(initialInterval) < 0) {
            throw new IllegalArgumentException("最大轮询间隔必须大于等于初始轮询间隔");
        }
        if (multiplier < 1.0D) {
            throw new IllegalArgumentException("轮询退避倍率必须大于等于 1");
        }
        this.currentInterval = initialInterval;
        this.maxInterval = maxInterval;
        this.multiplier = multiplier;
    }

    /**
     * 返回本轮等待间隔并推进下一轮间隔
     *
     * <p>
     * 调用方拿到返回值后执行 sleep
     * 本方法内部会同步计算下一轮等待时间
     *
     * @return 本轮等待间隔
     */
    public Duration nextInterval() {
        // currentInterval 是本轮应该等待的时间，先保存下来用于返回给调用方 sleep
        Duration interval = currentInterval;
        // multiplier 允许配置为小数，使用 round 避免直接截断导致退避增长不明显
        long nextMillis = Math.round(currentInterval.toMillis() * multiplier);
        // 下一轮等待时间不能超过 maxInterval，避免长时间 OCR / ETL 时轮询间隔无限放大
        currentInterval = Duration.ofMillis(Math.min(nextMillis, maxInterval.toMillis()));
        // 返回本轮间隔，内部状态已经推进到下一轮
        return interval;
    }
}
