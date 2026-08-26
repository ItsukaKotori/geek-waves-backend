package com.geekwaves.monitor.service;

import com.geekwaves.monitor.dto.JvmMetricsDto;
import com.geekwaves.monitor.support.MetricsMath;
import org.springframework.stereotype.Service;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.List;

/**
 * JVM 指标:纯 JDK MXBean 实时构建(微秒级读取,无缓存必要)。
 * 有意不依赖 OSHI —— 受限环境下 OSHI 实例化可能失败,JVM 指标保持零依赖。
 */
@Service
public class JvmMetricsService {
    private final RuntimeMXBean runtimeMX = ManagementFactory.getRuntimeMXBean();
    private final MemoryMXBean memoryMX = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadMX = ManagementFactory.getThreadMXBean();
    private final List<GarbageCollectorMXBean> gcMXs = ManagementFactory.getGarbageCollectorMXBeans();

    public JvmMetricsDto metrics() {
        MemoryUsage heap = memoryMX.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryMX.getNonHeapMemoryUsage();

        List<JvmMetricsDto.PoolDto> pools = ManagementFactory.getMemoryPoolMXBeans().stream()
                .map(this::toPool)
                .toList();

        List<JvmMetricsDto.GcDto> gcs = gcMXs.stream()
                .map(gc -> new JvmMetricsDto.GcDto(
                        gc.getName(),
                        orZero(gc.getCollectionCount()),
                        orZero(gc.getCollectionTime())))
                .toList();

        return new JvmMetricsDto(
                System.getProperty("java.version"),
                runtimeMX.getVmName(),
                runtimeMX.getUptime(),
                new JvmMetricsDto.HeapDto(
                        heap.getInit(), heap.getMax(), heap.getUsed(), heap.getCommitted(),
                        heap.getMax() > 0 ? MetricsMath.percent(heap.getUsed(), heap.getMax()) : 0),
                pools,
                nonHeap.getUsed(), nonHeap.getCommitted(),
                new JvmMetricsDto.ThreadsDto(
                        threadMX.getThreadCount(), threadMX.getPeakThreadCount(),
                        threadMX.getDaemonThreadCount(), threadMX.getTotalStartedThreadCount()),
                gcs);
    }

    private JvmMetricsDto.PoolDto toPool(MemoryPoolMXBean pool) {
        MemoryUsage usage = pool.getUsage();
        long used = usage == null ? 0 : usage.getUsed();
        long max = usage == null || usage.getMax() < 0 ? 0 : usage.getMax();
        return new JvmMetricsDto.PoolDto(
                pool.getName(),
                pool.getType() == null ? "HEAP" : pool.getType().name(),
                used, max,
                MetricsMath.percent(used, max));
    }

    private static long orZero(Long v) {
        return v == null ? 0 : v;
    }
}
