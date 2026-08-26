package com.geekwaves.aggregation.framework;

import com.geekwaves.aggregation.domain.FrameworkWatch;
import com.geekwaves.aggregation.domain.mapper.FrameworkWatchMapper;
import com.geekwaves.monitor.support.SchedulerRunRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FrameworkSchedulerTest {

    private FrameworkWatchMapper watchMapper;
    private FrameworkFetchService fetchService;
    private ThreadPoolTaskExecutor executor;
    private FrameworkScheduler scheduler;

    @BeforeEach
    void setUp() {
        watchMapper = mock(FrameworkWatchMapper.class);
        fetchService = mock(FrameworkFetchService.class);
        executor = mock(ThreadPoolTaskExecutor.class);
        scheduler = new FrameworkScheduler(watchMapper, fetchService, executor, new SchedulerRunRegistry());
    }

    private static FrameworkWatch watch(long id, LocalDateTime lastReleaseAt) {
        FrameworkWatch w = new FrameworkWatch();
        w.setId(id);
        w.setName("fw-" + id);
        w.setGithubRepo("owner/repo-" + id);
        w.setLastReleaseAt(lastReleaseAt);
        return w;
    }

    @Test
    void scanDueSubmitsOnlyDueWatches() {
        FrameworkWatch neverFetched = watch(1L, null);
        FrameworkWatch stale = watch(2L, LocalDateTime.now().minusHours(7));
        FrameworkWatch fresh = watch(3L, LocalDateTime.now().minusHours(3));
        when(watchMapper.selectList(null)).thenReturn(List.of(neverFetched, stale, fresh));

        scheduler.scanDue();

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(executor, times(2)).execute(captor.capture());
        captor.getAllValues().forEach(Runnable::run);
        verify(fetchService).fetchNow(neverFetched);
        verify(fetchService).fetchNow(stale);
        verify(fetchService, never()).fetchNow(fresh);
    }

    @Test
    void scanDueSubmitsNothingWhenNoWatchDue() {
        when(watchMapper.selectList(null))
                .thenReturn(List.of(watch(1L, LocalDateTime.now().minusSeconds(30))));

        scheduler.scanDue();

        verify(executor, never()).execute(any());
    }
}
