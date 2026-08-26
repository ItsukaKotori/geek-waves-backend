package com.geekwaves.config.port;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import java.time.Duration;

public class LocalLockPort implements LockPort {
    private final Cache<String, Long> locks = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfter(new Expiry<String, Long>() {
                @Override
                public long expireAfterCreate(String key, Long value, long currentTime) {
                    return Math.max(0, value - currentTime);
                }

                @Override
                public long expireAfterUpdate(String key, Long value, long currentTime, long currentDuration) {
                    return Math.max(0, value - currentTime);
                }

                @Override
                public long expireAfterRead(String key, Long value, long currentTime, long currentDuration) {
                    return currentDuration;
                }
            })
            .build();

    @Override
    public boolean tryLock(String key, Duration ttl) {
        if (locks.getIfPresent(key) != null) {
            return false;
        }
        return locks.asMap().putIfAbsent(key, System.nanoTime() + ttl.toNanos()) == null;
    }

    @Override
    public void unlock(String key) {
        locks.invalidate(key);
    }
}
