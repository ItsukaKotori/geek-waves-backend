package com.geekwaves.aggregation.framework;

import com.geekwaves.aggregation.domain.FrameworkWatch;
import com.geekwaves.aggregation.domain.mapper.FrameworkWatchMapper;
import com.geekwaves.monitor.support.SchedulerRunRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class FrameworkScheduler {
    private final FrameworkWatchMapper watchMapper;
    private final FrameworkFetchService fetchService;
    private final ThreadPoolTaskExecutor fetchPool;
    private final SchedulerRunRegistry runRegistry;

    @Scheduled(fixedDelayString = "PT60S", initialDelayString = "PT120S")
    public void scanDue() {
        runRegistry.begin("framework-scan");
        try {
            List<FrameworkWatch> watches = watchMapper.selectList(null);
            LocalDateTime threshold = LocalDateTime.now().minusHours(6);
            int due = 0;
            for (FrameworkWatch w : watches) {
                if (w.getLastReleaseAt() == null || w.getLastReleaseAt().isBefore(threshold)) {
                    fetchPool.execute(() -> fetchService.fetchNow(w));
                    due++;
                }
            }
            runRegistry.end("framework-scan", due, null);
            log.info("framework scheduled scan: {} watches, {} due", watches.size(), due);
        } catch (Exception e) {
            runRegistry.end("framework-scan", 0, e.getMessage());
            throw e;
        }
    }
}
