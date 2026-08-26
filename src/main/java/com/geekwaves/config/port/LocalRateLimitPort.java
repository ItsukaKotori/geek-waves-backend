package com.geekwaves.config.port;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class LocalRateLimitPort implements RateLimitPort {
    private record Window(long expireAtNanos, AtomicInteger count) {}

    private final Cache<String, Window> windows = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfter(new Expiry<String, Window>() {
                @Override
                public long expireAfterCreate(String key, Window value, long currentTime) {
                    return Math.max(0, value.expireAtNanos() - currentTime);
                }

                @Override
                public long expireAfterUpdate(String key, Window value, long currentTime, long currentDuration) {
                    return Math.max(0, value.expireAtNanos() - currentTime);
                }

                @Override
                public long expireAfterRead(String key, Window value, long currentTime, long currentDuration) {
                    return currentDuration;
                }
            })
            .build();

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        long now = System.nanoTime();
        long resetAt = now + window.toNanos();
        Window w = windows.asMap().compute(key, (k, old) -> {
            if (old == null || old.expireAtNanos() <= now) {
                return new Window(resetAt, new AtomicInteger(1));
            }
            old.count().incrementAndGet();
            return old;
        });
        return w.count().get() <= limit;
    }
}
