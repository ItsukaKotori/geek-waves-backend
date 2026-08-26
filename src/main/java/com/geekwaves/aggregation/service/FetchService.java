package com.geekwaves.aggregation.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.geekwaves.aggregation.adapter.AdapterRegistry;
import com.geekwaves.aggregation.domain.InfoSource;
import com.geekwaves.aggregation.domain.mapper.InfoSourceMapper;
import com.geekwaves.config.port.CachePort;
import com.geekwaves.config.port.LockPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@Slf4j
@RequiredArgsConstructor
public class FetchService {
    private final AdapterRegistry adapterRegistry;
    private final InfoSourceMapper sourceMapper;
    private final NewsService newsService;
    private final CachePort cachePort;
    private final LockPort lockPort;
    static final Duration LOCK_TTL = Duration.ofMinutes(10);
    static final int MAX_BACKOFF = 8;

    public boolean fetchNow(InfoSource source) {
        if (!lockPort.tryLock("fetch:lock:" + source.getId(), LOCK_TTL)) return false;
        try {
            var adapter = adapterRegistry.match(source.getType());
            if (adapter.isEmpty()) {
                source.setLastFetchStatus("SKIPPED");
                source.setLastError("无适配器: " + source.getType());
                sourceMapper.updateById(source);
                return false;
            }
            log.info("fetch source={} adapter={}", source.getCode(), source.getType());
            var since = source.getLastFetchAt() == null ? null : source.getLastFetchAt().atZone(ZoneId.systemDefault()).toInstant();
            var items = adapter.get().fetch(source, since);
            int created = newsService.persist(source, items);
            source.setLastFetchAt(LocalDateTime.now());
            source.setLastFetchStatus("SUCCESS");
            source.setLastError(null);
            source.setFailCount(0);
            sourceMapper.update(null, new UpdateWrapper<InfoSource>()
                    .eq("id", source.getId())
                    .set("last_fetch_at", source.getLastFetchAt())
                    .set("last_fetch_status", "SUCCESS")
                    .set("last_error", null)
                    .set("fail_count", 0));
            cachePort.invalidateByPrefix("news:list:");
            log.info("source {} fetched {} items ({} new)", source.getCode(), items.size(), created);
            return true;
        } catch (Exception e) {
            source.setLastFetchStatus("FAILED");
            source.setLastError(truncate(e.getMessage(), 1000));
            source.setFailCount(source.getFailCount() == null ? 1 : source.getFailCount() + 1);
            sourceMapper.updateById(source);
            log.warn("source {} fetch failed: {}", source.getCode(), e.getMessage());
            return false;
        } finally {
            lockPort.unlock("fetch:lock:" + source.getId());
        }
    }

    public boolean isDue(InfoSource source) {
        if (!Boolean.TRUE.equals(source.getEnabled())) return false;
        if (source.getLastFetchAt() == null) return true;
        int base = source.getRefreshMinutes() == null ? 30 : source.getRefreshMinutes();
        int fail = source.getFailCount() == null ? 0 : source.getFailCount();
        int factor = fail >= 3 ? Math.max(2, Math.min(fail - 1, MAX_BACKOFF)) : 1;
        long interval = (long) base * factor * 60;
        return source.getLastFetchAt().plusSeconds(interval).isBefore(LocalDateTime.now());
    }

    private String truncate(String msg, int len) {
        if (msg == null) return "unknown error";
        return msg.length() > len ? msg.substring(0, len) : msg;
    }
}
