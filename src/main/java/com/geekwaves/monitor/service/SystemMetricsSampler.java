package com.geekwaves.monitor.service;

import com.geekwaves.config.MonitorProperties;
import com.geekwaves.monitor.dto.SystemMetricsDto;
import com.geekwaves.monitor.support.MetricsMath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统指标后台采样器:唯一持有 SystemInfo 的组件,Controller 只读快照。
 * CPU/网速为区间型指标,依赖上一拍差分;静态信息(OS/核心数/磁盘容量)降频缓存。
 * 任何异常都降级为带 error 的 DTO,绝不抛出。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SystemMetricsSampler {
    private final MonitorProperties props;
    private final SystemInfo systemInfo = new SystemInfo();

    private volatile SystemMetricsDto snapshot;

    private long[] prevTicks;
    private long[][] prevPerCore;
    private final Map<String, long[]> prevNetBytes = new HashMap<>();
    private long prevSampleMs;

    /** 静态信息:首次采样后缓存 */
    private String osName;
    private String osVersion;
    private String hostName;
    private int logicalCores;
    private int physicalCores;
    private List<SystemMetricsDto.DiskDto> cachedDisks = List.of();
    private int cycle;

    @Scheduled(fixedDelayString = "${geekwaves.monitor.sample-ms:5000}", initialDelayString = "PT2S")
    public void sample() {
        if (!props.isEnabled()) return;
        try {
            snapshot = doSample();
        } catch (Throwable t) {
            log.warn("system metrics sample failed: {}", t.getMessage());
            snapshot = SystemMetricsDto.degraded(System.currentTimeMillis(), truncate(t.toString(), 300));
        }
    }

    public SystemMetricsDto latest() {
        return snapshot;
    }

    private SystemMetricsDto doSample() {
        long now = System.currentTimeMillis();
        long elapsed = prevSampleMs == 0 ? 0 : now - prevSampleMs;

        HardwareAbstractionLayer hw = systemInfo.getHardware();
        OperatingSystem os = systemInfo.getOperatingSystem();
        CentralProcessor cpu = hw.getProcessor();

        if (osName == null) {
            osName = os.getManufacturer() + " " + os.getFamily();
            osVersion = os.getVersionInfo().getVersion();
            hostName = os.getNetworkParams().getHostName();
            logicalCores = cpu.getLogicalProcessorCount();
            physicalCores = cpu.getPhysicalProcessorCount();
        }

        // CPU(区间差分;首拍无基线为 0)
        long[] ticks = cpu.getSystemCpuLoadTicks();
        double usage = prevTicks == null ? 0 : clampPercent(cpu.getSystemCpuLoadBetweenTicks(prevTicks) * 100);
        long[][] perCoreTicks = cpu.getProcessorCpuLoadTicks();
        List<Double> perCore = new ArrayList<>();
        if (prevPerCore != null) {
            for (double d : cpu.getProcessorCpuLoadBetweenTicks(prevPerCore)) {
                perCore.add(clampPercent(d * 100));
            }
        }
        prevTicks = ticks;
        prevPerCore = perCoreTicks;

        double[] loadAvg = cpu.getSystemLoadAverage(3);
        double load1 = loadAvg.length > 0 ? Math.max(0, loadAvg[0]) : 0;
        double load5 = loadAvg.length > 1 ? Math.max(0, loadAvg[1]) : 0;
        double load15 = loadAvg.length > 2 ? Math.max(0, loadAvg[2]) : 0;

        // 内存(swap 在 VirtualMemory)
        GlobalMemory mem = hw.getMemory();
        long memTotal = mem.getTotal();
        long memAvail = mem.getAvailable();
        oshi.hardware.VirtualMemory vm = mem.getVirtualMemory();
        SystemMetricsDto.MemoryDto memory = new SystemMetricsDto.MemoryDto(
                memTotal, memTotal - memAvail, memAvail,
                MetricsMath.percent(memTotal - memAvail, memTotal),
                vm.getSwapTotal(), vm.getSwapUsed());

        // 磁盘:容量/分区列表变化极慢,每 5 个周期刷新一次
        if (cycle % 5 == 0) {
            List<SystemMetricsDto.DiskDto> disks = new ArrayList<>();
            for (OSFileStore fs : os.getFileSystem().getFileStores()) {
                long total = fs.getTotalSpace();
                long usable = fs.getUsableSpace();
                disks.add(new SystemMetricsDto.DiskDto(
                        fs.getName(), fs.getMount(), fs.getType(),
                        total, usable, MetricsMath.percent(total - usable, total)));
            }
            cachedDisks = disks;
        }

        // 网络:逐网卡刷新计数器并与上一拍求速率
        List<SystemMetricsDto.NetDto> networks = new ArrayList<>();
        for (NetworkIF nif : hw.getNetworkIFs()) {
            nif.updateAttributes();
            long[] prev = prevNetBytes.get(nif.getName());
            long rx = prev == null ? 0 : MetricsMath.byteRate(prev[0], nif.getBytesRecv(), elapsed);
            long tx = prev == null ? 0 : MetricsMath.byteRate(prev[1], nif.getBytesSent(), elapsed);
            prevNetBytes.put(nif.getName(), new long[]{nif.getBytesRecv(), nif.getBytesSent()});
            networks.add(new SystemMetricsDto.NetDto(nif.getName(), nif.getDisplayName(), rx, tx));
        }

        prevSampleMs = now;
        cycle++;

        return new SystemMetricsDto(
                osName, osVersion, System.getProperty("os.arch"), hostName,
                os.getSystemBootTime() * 1000, os.getSystemUptime(),
                new SystemMetricsDto.CpuDto(logicalCores, physicalCores, usage, perCore, load1, load5, load15),
                memory, cachedDisks, networks, now, null);
    }

    private static double clampPercent(double v) {
        return Math.max(0.0, Math.min(100.0, v));
    }

    private static String truncate(String s, int len) {
        if (s == null) return "unknown";
        return s.length() > len ? s.substring(0, len) : s;
    }
}
