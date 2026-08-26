package com.geekwaves.monitor.dto;

import java.util.List;

/**
 * 系统级指标快照(OSHI 采样)。
 * error 非空表示采样降级(前端应置灰展示);null 字段仅在降级时出现。
 */
public record SystemMetricsDto(
        String osName,
        String osVersion,
        String arch,
        String hostName,
        long bootEpochMs,
        long uptimeSeconds,
        CpuDto cpu,
        MemoryDto memory,
        List<DiskDto> disks,
        List<NetDto> networks,
        long sampledAt,
        String error) {

    public record CpuDto(
            int logicalCores,
            int physicalCores,
            double usagePercent,
            List<Double> perCoreUsage,
            double load1,
            double load5,
            double load15) {
    }

    public record MemoryDto(
            long totalBytes,
            long usedBytes,
            long availableBytes,
            double usagePercent,
            long swapTotalBytes,
            long swapUsedBytes) {
    }

    public record DiskDto(
            String name,
            String mount,
            String fsType,
            long totalBytes,
            long availableBytes,
            double usagePercent) {
    }

    public record NetDto(
            String name,
            String displayName,
            long rxBytesPerSec,
            long txBytesPerSec) {
    }

    /** 采样失败时的降级结构:仅有 error 与 sampledAt,其余字段为零值/空 */
    public static SystemMetricsDto degraded(long sampledAt, String error) {
        return new SystemMetricsDto(null, null, null, null, 0, 0,
                null, null, List.of(), List.of(), sampledAt, error);
    }
}
