package com.geekwaves.config.port;

import java.time.Duration;

public interface RateLimitPort {
    boolean tryAcquire(String key, int limit, Duration window);
}
