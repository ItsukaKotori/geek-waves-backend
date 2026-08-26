package com.geekwaves.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.geekwaves.config.MonitorProperties;
import com.geekwaves.monitor.domain.ProbeTarget;
import com.geekwaves.monitor.domain.mapper.ProbeTargetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.itsuka.core.exception.ServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;

/**
 * 端口服务探测:纯 TCP connect。
 * 定时扫描到期目标并提交 fetchPool(天然并发上限);手动探测同步执行(单次 ≤5s)。
 * 本功能的目的就是探测本机/内网,SsrFGuard 不适用;防线在 Validator + 数量上限。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PortProbeService {
    private final ProbeTargetMapper mapper;
    private final MonitorProperties props;
    private final ThreadPoolTaskExecutor fetchPool;

    @Scheduled(fixedDelayString = "${geekwaves.monitor.probe-fixed-delay-ms:30000}", initialDelayString = "PT10S")
    public void probeDue() {
        if (!props.isEnabled()) return;
        for (ProbeTarget t : mapper.selectList(new QueryWrapper<ProbeTarget>().eq("enabled", true))) {
            if (isDue(t)) {
                fetchPool.execute(() -> {
                    try {
                        probeOne(t);
                    } catch (Exception e) {
                        log.warn("probe {}:{} failed: {}", t.getHost(), t.getPort(), e.getMessage());
                    }
                });
            }
        }
    }

    boolean isDue(ProbeTarget t) {
        if (t.getLastProbeAt() == null) return true;
        int interval = t.getIntervalSeconds() == null ? 60 : t.getIntervalSeconds();
        return t.getLastProbeAt().plusSeconds(interval).isBefore(LocalDateTime.now());
    }

    /** 同步探测单个目标并落库,返回更新后的实体 */
    public ProbeTarget probeOne(ProbeTarget t) {
        long t0 = System.nanoTime();
        String status;
        Integer latency = null;
        String error = null;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(t.getHost(), t.getPort()), effectiveTimeout(t));
            latency = (int) ((System.nanoTime() - t0) / 1_000_000);
            status = "UP";
        } catch (SocketTimeoutException e) {
            status = "TIMEOUT";
            error = "连接超时";
        } catch (IOException | RuntimeException e) {
            status = "DOWN";
            error = truncate(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), 500);
        }

        LocalDateTime now = LocalDateTime.now();
        int nextFail = "UP".equals(status) ? 0 : (t.getFailCount() == null ? 1 : t.getFailCount() + 1);

        mapper.update(null, new UpdateWrapper<ProbeTarget>()
                .eq("id", t.getId())
                .set("last_probe_at", now)
                .set("last_status", status)
                .set("last_latency_ms", latency)
                .set("last_error", error)
                .set("fail_count", nextFail));

        t.setLastProbeAt(now);
        t.setLastStatus(status);
        t.setLastLatencyMs(latency);
        t.setLastError(error);
        t.setFailCount(nextFail);
        return t;
    }

    /** 立即同步探测(手动按钮):立即返回最新结果 */
    public ProbeTarget manualProbe(Long id) {
        ProbeTarget t = mapper.selectById(id);
        if (t == null) throw ServiceException.create(HttpStatus.NOT_FOUND, "探测目标不存在");
        return probeOne(t);
    }

    private int effectiveTimeout(ProbeTarget t) {
        Integer ms = t.getTimeoutMs();
        if (ms == null || ms < 200 || ms > 5000) return 2000;
        return ms;
    }

    private static String truncate(String s, int len) {
        if (s == null) return "unknown";
        return s.length() > len ? s.substring(0, len) : s;
    }
}
