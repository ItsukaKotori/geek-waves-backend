package com.geekwaves.monitor.dto;

import java.util.List;

/** JVM 指标(MXBean 实时构建,无缓存) */
public record JvmMetricsDto(
        String javaVersion,
        String jvmName,
        long uptimeMs,
        HeapDto heap,
        List<PoolDto> pools,
        long nonHeapUsedBytes,
        long nonHeapCommittedBytes,
        ThreadsDto threads,
        List<GcDto> gcs) {

    public record HeapDto(
            long initBytes,
            long maxBytes,
            long usedBytes,
            long committedBytes,
            double usagePercent) {
    }

    /** type: HEAP / NON_HEAP */
    public record PoolDto(
            String name,
            String type,
            long usedBytes,
            long maxBytes,
            double usagePercent) {
    }

    public record ThreadsDto(
            int live,
            int peak,
            int daemon,
            long started) {
    }

    public record GcDto(
            String collector,
            long count,
            long totalTimeMs) {
    }
}
