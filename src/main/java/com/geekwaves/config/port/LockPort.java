package com.geekwaves.config.port;

import java.time.Duration;

public interface LockPort {
    boolean tryLock(String key, Duration ttl);
    void unlock(String key);
}
