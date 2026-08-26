package com.geekwaves.aggregation.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.geekwaves.aggregation.domain.InfoSource;
import com.geekwaves.aggregation.domain.mapper.InfoSourceMapper;
import com.geekwaves.aggregation.service.FetchService;
import com.geekwaves.monitor.support.SchedulerRunRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class FetchScheduler {
    private final InfoSourceMapper sourceMapper;
    private final FetchService fetchService;
    private final ThreadPoolTaskExecutor fetchPool;
    private final SchedulerRunRegistry runRegistry;

    @Scheduled(fixedDelayString = "PT60S", initialDelayString = "PT30S")
    public void scanAndFetch() {
        runRegistry.begin("fetch-scan");
        try {
            List<InfoSource> sources = sourceMapper.selectList(new QueryWrapper<InfoSource>().eq("enabled", true));
            int due = 0;
            for (InfoSource s : sources) {
                if (fetchService.isDue(s)) {
                    fetchPool.execute(() -> fetchService.fetchNow(s));
                    due++;
                }
            }
            runRegistry.end("fetch-scan", due, null);
            log.info("scheduled scan: {} enabled sources, {} due", sources.size(), due);
        } catch (Exception e) {
            runRegistry.end("fetch-scan", 0, e.getMessage());
            throw e;
        }
    }
}
