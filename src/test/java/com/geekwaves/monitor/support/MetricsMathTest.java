package com.geekwaves.monitor.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricsMathTest {

    @Test
    void byteRate_positiveDeltaDividedByElapsed() {
        // 1000 字节 / 500ms = 2000 B/s
        assertEquals(2000, MetricsMath.byteRate(1_000_000L, 1_001_000L, 500));
    }

    @Test
    void byteRate_counterResetYieldsZero() {
        // 网卡计数器重置(cur < prev)不应产生负速率
        assertEquals(0, MetricsMath.byteRate(5_000L, 100L, 1000));
    }

    @Test
    void byteRate_zeroElapsedYieldsZero() {
        assertEquals(0, MetricsMath.byteRate(1_000L, 2_000L, 0));
    }

    @Test
    void percent_usedOverTotal() {
        assertEquals(50.0, MetricsMath.percent(50, 100), 1e-9);
        assertEquals(0.0, MetricsMath.percent(0, 100), 1e-9);
        assertEquals(100.0, MetricsMath.percent(100, 100), 1e-9);
    }

    @Test
    void percent_zeroOrNegativeTotalYieldsZero() {
        assertEquals(0.0, MetricsMath.percent(10, 0), 1e-9);
        assertEquals(0.0, MetricsMath.percent(10, -5), 1e-9);
    }

    @Test
    void processCpuPercent_fullBurnSingleCoreIs100() {
        // 单核上 100ms 内消耗 100ms CPU → 100%
        assertEquals(100.0, MetricsMath.processCpuPercent(0L, 100_000_000L, 100, 1), 1e-6);
    }

    @Test
    void processCpuPercent_multiCoreNormalizedAndClamped() {
        // 8 核上 100ms 内消耗 400ms CPU → 400/100/8*100 = 50%
        assertEquals(50.0, MetricsMath.processCpuPercent(0L, 400_000_000L, 100, 8), 1e-6);
        // 超限(数值异常)夹紧到 100
        assertEquals(100.0, MetricsMath.processCpuPercent(0L, 100_000_000_000L, 100, 8), 1e-6);
    }

    @Test
    void processCpuPercent_zeroElapsedOrCoresYieldsZero() {
        assertEquals(0.0, MetricsMath.processCpuPercent(0L, 100L, 0, 4), 1e-9);
        assertEquals(0.0, MetricsMath.processCpuPercent(0L, 100L, 100, 0), 1e-9);
    }
}
