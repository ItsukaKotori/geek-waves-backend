package com.geekwaves.config.port;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LocalStoragePortsTest {
    private ObjectMapper mapper = JsonMapper.builder().build();
    private LocalCachePort cache;
    private LocalLockPort lock;
    private LocalRateLimitPort rate;

    @BeforeEach
    void setUp() {
        cache = new LocalCachePort(mapper);
        lock = new LocalLockPort();
        rate = new LocalRateLimitPort();
    }

    @Test
    void cachePutGetInvalidate() {
        cache.put("k", List.of(1, 2, 3), Duration.ofMinutes(1));
        Optional<List> got = cache.get("k", java.util.List.class);
        assertTrue(got.isPresent());
        cache.invalidate("k");
        assertTrue(cache.get("k", java.util.List.class).isEmpty());
    }

    @Test
    void cacheExpires() {
        cache.put("k", "v", Duration.ofMillis(50));
        try {
            Thread.sleep(80);
        } catch (InterruptedException ignored) {
        }
        assertTrue(cache.get("k", String.class).isEmpty());
    }

    @Test
    void lockMutualExclusion() {
        assertTrue(lock.tryLock("a", Duration.ofSeconds(2)));
        assertFalse(lock.tryLock("a", Duration.ofSeconds(2)));
        lock.unlock("a");
        assertTrue(lock.tryLock("a", Duration.ofSeconds(2)));
    }

    @Test
    void rateLimitWithinWindow() {
        assertTrue(rate.tryAcquire("r", 3, Duration.ofSeconds(5)));
        assertTrue(rate.tryAcquire("r", 3, Duration.ofSeconds(5)));
        assertTrue(rate.tryAcquire("r", 3, Duration.ofSeconds(5)));
        assertFalse(rate.tryAcquire("r", 3, Duration.ofSeconds(5)));
    }

    @Test
    void lockConcurrentMutualExclusion() throws Exception {
        int threads = 16;
        int rounds = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int round = 0; round < rounds; round++) {
                CountDownLatch go = new CountDownLatch(1);
                AtomicInteger winners = new AtomicInteger(0);
                List<Future<?>> futures = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    futures.add(pool.submit(() -> {
                        try {
                            go.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(e);
                        }
                        if (lock.tryLock("mutex", Duration.ofSeconds(30))) {
                            winners.incrementAndGet();
                        }
                    }));
                }
                go.countDown();
                for (Future<?> f : futures) {
                    f.get(5, TimeUnit.SECONDS);
                }
                assertEquals(1, winners.get(), "round=" + round);
                lock.unlock("mutex");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void cacheInvalidateByPrefix() {
        cache.put("p1:a", "a", Duration.ofMinutes(1));
        cache.put("p1:b", "b", Duration.ofMinutes(1));
        cache.put("p2:a", "c", Duration.ofMinutes(1));
        cache.invalidateByPrefix("p1:");
        assertTrue(cache.get("p1:a", String.class).isEmpty());
        assertTrue(cache.get("p1:b", String.class).isEmpty());
        assertTrue(cache.get("p2:a", String.class).isPresent());
    }

    @Test
    void rateLimitWindowReset() throws InterruptedException {
        rate.tryAcquire("rw", 1, Duration.ofMillis(50));
        assertFalse(rate.tryAcquire("rw", 1, Duration.ofMillis(50)));
        Thread.sleep(80);
        assertTrue(rate.tryAcquire("rw", 1, Duration.ofMillis(50)));
    }

    @Test
    void lockExpiredThenReacquire() throws InterruptedException {
        lock.tryLock("e", Duration.ofMillis(50));
        Thread.sleep(80);
        assertTrue(lock.tryLock("e", Duration.ofSeconds(2)));
    }

    @Test
    void unlockIdempotent() {
        lock.tryLock("u", Duration.ofSeconds(2));
        lock.unlock("u");
        lock.unlock("u");
        assertTrue(lock.tryLock("u", Duration.ofSeconds(2)));
    }
}
