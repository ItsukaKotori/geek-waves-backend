package com.geekwaves.aggregation.scheduler;

import com.geekwaves.aggregation.domain.InfoSource;
import com.geekwaves.aggregation.domain.mapper.InfoSourceMapper;
import com.geekwaves.aggregation.service.FetchService;
import com.geekwaves.monitor.support.SchedulerRunRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FetchSchedulerTest {
    private InfoSourceMapper sourceMapper;
    private FetchService fetchService;
    private ThreadPoolTaskExecutor executor;
    private FetchScheduler scheduler;

    @BeforeEach
    void setUp() {
        sourceMapper = mock(InfoSourceMapper.class);
        fetchService = mock(FetchService.class);
        executor = mock(ThreadPoolTaskExecutor.class);
        scheduler = new FetchScheduler(sourceMapper, fetchService, executor, new SchedulerRunRegistry());
    }

    @Test
    void scanAndFetchSubmitsOnlyDueSources() {
        InfoSource due = source(1L);
        InfoSource notDue = source(2L);
        when(sourceMapper.selectList(any())).thenReturn(List.of(due, notDue));
        when(fetchService.isDue(due)).thenReturn(true);
        when(fetchService.isDue(notDue)).thenReturn(false);

        scheduler.scanAndFetch();

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(captor.capture());
        captor.getValue().run();
        verify(fetchService).fetchNow(due);
    }

    @Test
    void scanAndFetchSubmitsNothingWhenNoneDue() {
        InfoSource s = source(1L);
        when(sourceMapper.selectList(any())).thenReturn(List.of(s));
        when(fetchService.isDue(s)).thenReturn(false);

        scheduler.scanAndFetch();

        verify(executor, never()).execute(any());
    }

    @Test
    void scanAndFetchQueryFiltersEnabled() {
        when(sourceMapper.selectList(any())).thenReturn(List.of());

        scheduler.scanAndFetch();

        verify(sourceMapper).selectList(argThat(w -> w.getSqlSegment().contains("enabled")));
    }

    private InfoSource source(long id) {
        InfoSource s = new InfoSource();
        s.setId(id);
        s.setCode("src-" + id);
        s.setEnabled(true);
        return s;
    }
}
