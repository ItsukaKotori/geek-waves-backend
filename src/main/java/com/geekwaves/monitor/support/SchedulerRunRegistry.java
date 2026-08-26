package com.geekwaves.monitor.support;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 调度器最近一次运行的内存记录(非持久化,重启即清零)。
 * 供调度器以最低侵入方式登记 begin/end,监控页只读取快照。
 */
@Component
public class SchedulerRunRegistry {

    public record RunInfo(String name, LocalDateTime lastStartAt, Long lastDurationMs,
                          Integer itemCount, String lastError) {
    }

    private final Map<String, LocalDateTime> runningSince = new ConcurrentHashMap<>();
    private final Map<String, RunInfo> finished = new ConcurrentHashMap<>();

    public void begin(String name) {
        runningSince.put(name, LocalDateTime.now());
    }

    public void end(String name, int itemCount, String error) {
        LocalDateTime start = runningSince.remove(name);
        if (start == null) return;
        long durationMs = java.time.Duration.between(start, LocalDateTime.now()).toMillis();
        finished.put(name, new RunInfo(name, start, durationMs, itemCount, error));
    }

    /** 防御性拷贝;运行中(未 end)的任务带 lastStartAt 但无 duration */
    public Map<String, RunInfo> snapshot() {
        Map<String, RunInfo> snap = new java.util.HashMap<>(finished);
        runningSince.forEach((name, start) ->
                snap.merge(name, new RunInfo(name, start, null, null, null),
                        (fin, run) -> new RunInfo(name, run.lastStartAt(), fin.lastDurationMs(), fin.itemCount(), fin.lastError())));
        return snap;
    }
}
