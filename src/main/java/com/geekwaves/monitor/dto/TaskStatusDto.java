package com.geekwaves.monitor.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 应用内任务状态聚合:调度器 / 抓取线程池 / 数据源健康 / AI Provider / 资讯总量 */
public record TaskStatusDto(
        List<SchedulerRunDto> schedulers,
        PoolStatusDto fetchPool,
        List<SourceHealthDto> sources,
        List<ProviderStatusDto> providers,
        long newsTotal,
        long generatedAt,
        String error) {

    /** 监控禁用时的降级结构:仅有 error 与 generatedAt,其余字段为零值/空 */
    public static TaskStatusDto degraded(long generatedAt, String error) {
        return new TaskStatusDto(List.of(), new PoolStatusDto(0, 0, 0),
                List.of(), List.of(), 0, generatedAt, error);
    }

    public record SchedulerRunDto(
            String name,
            LocalDateTime lastStartAt,
            Long lastDurationMs,
            Integer itemCount,
            String lastError) {
    }

    public record PoolStatusDto(
            int active,
            int poolSize,
            int queueSize) {
    }

    public record SourceHealthDto(
            Long id,
            String name,
            String code,
            Boolean enabled,
            String lastFetchStatus,
            LocalDateTime lastFetchAt,
            String lastError,
            Integer failCount,
            boolean dueNow) {
    }

    /** 只暴露脱敏状态,不携带 apiKeyEnc */
    public record ProviderStatusDto(
            Long id,
            String name,
            String vendor,
            String model,
            Boolean enabled,
            Boolean isDefault,
            boolean keySet) {
    }
}
