package com.geekwaves.config.port;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class LocalCachePort implements CachePort {
    private record Entry(String json, long expireAtNanos) {}

    private final ObjectMapper objectMapper;
    private final Cache<String, Entry> store = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfter(new Expiry<String, Entry>() {
                @Override
                public long expireAfterCreate(String key, Entry value, long currentTime) {
                    return Math.max(0, value.expireAtNanos() - currentTime);
                }

                @Override
                public long expireAfterUpdate(String key, Entry value, long currentTime, long currentDuration) {
                    return Math.max(0, value.expireAtNanos() - currentTime);
                }

                @Override
                public long expireAfterRead(String key, Entry value, long currentTime, long currentDuration) {
                    return currentDuration;
                }
            })
            .build();

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        Entry entry = store.getIfPresent(key);
        if (entry == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(entry.json(), type));
        } catch (Exception e) {
            store.invalidate(key);
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        try {
            store.put(key, new Entry(objectMapper.writeValueAsString(value), System.nanoTime() + ttl.toNanos()));
        } catch (Exception e) {
            log.debug("cache put skipped key={}", key, e);
        }
    }

    @Override
    public void invalidate(String key) {
        store.invalidate(key);
    }

    @Override
    public void invalidateByPrefix(String prefix) {
        store.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }
}
