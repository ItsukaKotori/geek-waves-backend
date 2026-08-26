package com.geekwaves.monitor.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulerRunRegistryTest {

    @Test
    void beginThenEndRecordsDurationAndItemCount() throws Exception {
        SchedulerRunRegistry registry = new SchedulerRunRegistry();
        registry.begin("fetch-scan");
        Thread.sleep(5);
        registry.end("fetch-scan", 3, null);

        SchedulerRunRegistry.RunInfo info = registry.snapshot().get("fetch-scan");
        assertNotNull(info);
        assertNotNull(info.lastStartAt());
        assertNotNull(info.lastDurationMs());
        assertTrue(info.lastDurationMs() >= 5);
        assertEquals(3, info.itemCount());
        assertNull(info.lastError());
    }

    @Test
    void endWithErrorKeepsLastError() {
        SchedulerRunRegistry registry = new SchedulerRunRegistry();
        registry.begin("framework-scan");
        registry.end("framework-scan", 0, "boom");

        SchedulerRunRegistry.RunInfo info = registry.snapshot().get("framework-scan");
        assertEquals("boom", info.lastError());
        assertEquals(0, info.itemCount());
    }

    @Test
    void runningWithoutEndHasNoDuration() {
        SchedulerRunRegistry registry = new SchedulerRunRegistry();
        registry.begin("fetch-scan");

        SchedulerRunRegistry.RunInfo info = registry.snapshot().get("fetch-scan");
        assertNotNull(info);
        assertNull(info.lastDurationMs());
    }

    @Test
    void repeatedRunsOverwritePrevious() {
        SchedulerRunRegistry registry = new SchedulerRunRegistry();
        registry.begin("fetch-scan");
        registry.end("fetch-scan", 1, null);
        registry.begin("fetch-scan");
        registry.end("fetch-scan", 2, null);

        assertEquals(2, registry.snapshot().get("fetch-scan").itemCount());
    }

    @Test
    void snapshotIsDefensiveCopy() {
        SchedulerRunRegistry registry = new SchedulerRunRegistry();
        registry.begin("fetch-scan");
        registry.end("fetch-scan", 1, null);

        var snap1 = registry.snapshot();
        snap1.clear();
        assertEquals(1, registry.snapshot().size());
    }
}
