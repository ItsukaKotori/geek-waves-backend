package com.geekwaves.monitor.service;

import com.geekwaves.config.MonitorProperties;
import com.geekwaves.monitor.dto.ProcessSnapshotDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 进程快照采样器:独立慢周期(进程枚举最重,数百 ms 量级)。
 * CPU% = OSHI 区间负载(每核语义)÷ 逻辑核数 → [0,100];
 * 保留 top N(按 CPU 降序、RSS 兜底),不含 commandLine(泄密面)。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProcessMetricsSampler {
    private final MonitorProperties props;
    private final SystemInfo systemInfo = new SystemInfo();

    private volatile ProcessSnapshotDto snapshot;

    /** 上一拍进程快照(pid → snapshot),供区间 CPU 计算 */
    private Map<Integer, OSProcess> prevProcesses = Map.of();

    @Scheduled(fixedDelayString = "${geekwaves.monitor.process-sample-ms:10000}", initialDelayString = "PT15S")
    public void sample() {
        if (!props.isEnabled()) return;
        try {
            snapshot = doSample();
        } catch (Throwable t) {
            log.warn("process metrics sample failed: {}", t.getMessage());
            snapshot = ProcessSnapshotDto.degraded(System.currentTimeMillis(), truncate(t.toString(), 300));
        }
    }

    public ProcessSnapshotDto latest() {
        return snapshot;
    }

    private ProcessSnapshotDto doSample() {
        OperatingSystem os = systemInfo.getOperatingSystem();
        int logicalCores = Math.max(1, systemInfo.getHardware().getProcessor().getLogicalProcessorCount());

        List<OSProcess> current = os.getProcesses(null, null, 0);
        Map<Integer, OSProcess> nextPrev = new HashMap<>();

        List<ProcessSnapshotDto.ProcessDto> rows = new ArrayList<>(current.size());
        for (OSProcess p : current) {
            double rawLoad = p.getProcessCpuLoadBetweenTicks(prevProcesses.get(p.getProcessID()));
            double cpuPercent = Math.max(0.0, Math.min(100.0, rawLoad * 100.0 / logicalCores));
            rows.add(new ProcessSnapshotDto.ProcessDto(
                    p.getProcessID(),
                    emptyIfNull(p.getName()),
                    emptyIfNull(p.getUser()),
                    p.getState() == null ? "UNKNOWN" : p.getState().name(),
                    cpuPercent,
                    p.getResidentMemory()));
            nextPrev.put(p.getProcessID(), p);
        }
        prevProcesses = nextPrev;

        rows.sort(Comparator.comparingDouble(ProcessSnapshotDto.ProcessDto::cpuPercent).reversed()
                .thenComparing(Comparator.comparingLong(ProcessSnapshotDto.ProcessDto::rssBytes).reversed()));
        List<ProcessSnapshotDto.ProcessDto> top = rows.stream().limit(props.getProcessTopN()).toList();

        return new ProcessSnapshotDto(System.currentTimeMillis(), current.size(), top, null);
    }

    private static String emptyIfNull(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int len) {
        if (s == null) return "unknown";
        return s.length() > len ? s.substring(0, len) : s;
    }
}
