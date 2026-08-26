package com.geekwaves.monitor.support;

/**
 * 指标差分计算:采样器两次快照之间的纯数学换算。
 * 独立成类以便单测覆盖(计数器重置/夹紧等边界)。
 */
public final class MetricsMath {

    private MetricsMath() {
    }

    /** 网卡速率:B/s。计数器重置(cur &lt; prev)或时间差为 0 时返回 0,不产生负值 */
    public static long byteRate(long prevBytes, long curBytes, long elapsedMs) {
        if (elapsedMs <= 0) return 0;
        long delta = curBytes - prevBytes;
        if (delta <= 0) return 0;
        return delta * 1000 / elapsedMs;
    }

    /** 使用率百分比 [0,100];total &lt;= 0 视为未知返回 0 */
    public static double percent(long used, long total) {
        if (total <= 0) return 0.0;
        double p = used * 100.0 / total;
        return Math.max(0.0, Math.min(100.0, p));
    }

    /**
     * 进程 CPU 百分比(已按逻辑核数归一,区间 [0,100])。
     * 单核满载 = 100;多核下消耗 ns 分布到所有核。
     */
    public static double processCpuPercent(long prevCpuNs, long curCpuNs, long elapsedMs, int logicalCores) {
        if (elapsedMs <= 0 || logicalCores <= 0) return 0.0;
        long elapsedNs = elapsedMs * 1_000_000L;
        double raw = (curCpuNs - prevCpuNs) * 100.0 / elapsedNs / logicalCores * 1;
        return Math.max(0.0, Math.min(100.0, raw));
    }
}
