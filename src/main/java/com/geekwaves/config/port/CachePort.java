package com.geekwaves.config.port;

import java.time.Duration;
import java.util.Optional;

public interface CachePort {
    <T> Optional<T> get(String key, Class<T> type);
    void put(String key, Object value, Duration ttl);
    void invalidate(String key);
    void invalidateByPrefix(String prefix);
}
