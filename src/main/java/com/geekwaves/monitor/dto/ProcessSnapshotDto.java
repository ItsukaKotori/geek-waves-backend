package com.geekwaves.monitor.dto;

import java.util.List;

/** 进程快照:top N(后端已按 CPU 降序)。刻意不含 commandLine(泄密面) */
public record ProcessSnapshotDto(
        long sampledAt,
        int total,
        List<ProcessDto> processes,
        String error) {

    public record ProcessDto(
            long pid,
            String name,
            String user,
            String state,
            double cpuPercent,
            long rssBytes) {
    }

    public static ProcessSnapshotDto degraded(long sampledAt, String error) {
        return new ProcessSnapshotDto(sampledAt, 0, List.of(), error);
    }
}
