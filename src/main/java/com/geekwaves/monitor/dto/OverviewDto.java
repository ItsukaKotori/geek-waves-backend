package com.geekwaves.monitor.dto;

/** 概览组合:系统 + JVM,一次请求供前端 5s 轮询 */
public record OverviewDto(
        SystemMetricsDto system,
        JvmMetricsDto jvm) {
}
