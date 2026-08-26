package com.geekwaves.monitor.service;

import com.geekwaves.aggregation.domain.InfoSource;
import com.geekwaves.aggregation.domain.mapper.InfoSourceMapper;
import com.geekwaves.aggregation.domain.mapper.NewsItemMapper;
import com.geekwaves.aggregation.service.FetchService;
import com.geekwaves.ai.domain.AiProvider;
import com.geekwaves.ai.domain.mapper.AiProviderMapper;
import com.geekwaves.monitor.dto.TaskStatusDto;
import com.geekwaves.monitor.support.SchedulerRunRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskStatusServiceTest {

    @Mock
    private InfoSourceMapper sourceMapper;
    @Mock
    private AiProviderMapper providerMapper;
    @Mock
    private NewsItemMapper newsItemMapper;
    @Mock
    private FetchService fetchService;
    @Mock
    private ThreadPoolTaskExecutor fetchPool;
    @Mock
    private java.util.concurrent.ThreadPoolExecutor poolExecutor;

    private final SchedulerRunRegistry registry = new SchedulerRunRegistry();
    private TaskStatusService service;

    @BeforeEach
    void setUp() {
        service = new TaskStatusService(sourceMapper, providerMapper, newsItemMapper, fetchService, fetchPool, registry);
    }

    private InfoSource source(String code, boolean enabled, String status, Integer failCount) {
        InfoSource s = new InfoSource();
        s.setId(1L);
        s.setName("源-" + code);
        s.setCode(code);
        s.setEnabled(enabled);
        s.setLastFetchStatus(status);
        s.setLastFetchAt(LocalDateTime.now().minusMinutes(10));
        s.setFailCount(failCount);
        return s;
    }

    @Test
    void aggregatesSourcesWithDueNowFromFetchService() {
        InfoSource s1 = source("hn", true, "SUCCESS", 0);
        InfoSource s2 = source("rss", true, "FAILED", 3);
        when(sourceMapper.selectList(any())).thenReturn(List.of(s1, s2));
        when(fetchService.isDue(s1)).thenReturn(true);
        when(fetchService.isDue(s2)).thenReturn(false);
        when(newsItemMapper.selectCount(any())).thenReturn(42L);

        TaskStatusDto dto = service.status();

        assertEquals(2, dto.sources().size());
        TaskStatusDto.SourceHealthDto h1 = dto.sources().get(0);
        assertTrue(h1.dueNow());
        assertEquals("SUCCESS", h1.lastFetchStatus());
        assertFalse(dto.sources().get(1).dueNow());
        assertEquals(42, dto.newsTotal());
    }

    @Test
    void providersAreMaskedWithoutApiKeyEnc() {
        AiProvider p = new AiProvider();
        p.setId(7L);
        p.setName("DeepSeek");
        p.setVendor("OPENAI_COMPAT");
        p.setModel("deepseek-chat");
        p.setEnabled(true);
        p.setIsDefault(true);
        p.setApiKeyEnc("cipher-text");
        AiProvider p2 = new AiProvider();
        p2.setId(8L);
        p2.setName("无密钥");
        p2.setVendor("ANTHROPIC");
        p2.setEnabled(false);
        p2.setIsDefault(false);
        when(providerMapper.selectList(any())).thenReturn(List.of(p, p2));
        when(newsItemMapper.selectCount(any())).thenReturn(0L);

        TaskStatusDto dto = service.status();

        assertEquals(2, dto.providers().size());
        TaskStatusDto.ProviderStatusDto d1 = dto.providers().get(0);
        assertTrue(d1.keySet());
        assertTrue(d1.isDefault());
        assertFalse(dto.providers().get(1).keySet());
    }

    @Test
    void schedulerRegistrySnapshotIncluded() {
        registry.begin("fetch-scan");
        registry.end("fetch-scan", 5, null);

        TaskStatusDto dto = service.status();

        assertEquals(1, dto.schedulers().size());
        TaskStatusDto.SchedulerRunDto run = dto.schedulers().get(0);
        assertEquals("fetch-scan", run.name());
        assertEquals(5, run.itemCount());
        assertNull(run.lastError());
        assertNotNull(run.lastDurationMs());
    }

    @Test
    void fetchPoolStatusExposed() {
        lenient().when(fetchPool.getActiveCount()).thenReturn(2);
        lenient().when(fetchPool.getPoolSize()).thenReturn(4);
        when(fetchPool.getThreadPoolExecutor()).thenReturn(poolExecutor);
        when(poolExecutor.getQueue()).thenReturn(new java.util.concurrent.LinkedBlockingQueue<Runnable>(
                java.util.List.of(() -> {}, () -> {})));
        when(newsItemMapper.selectCount(any())).thenReturn(0L);

        TaskStatusDto dto = service.status();

        assertEquals(2, dto.fetchPool().active());
        assertEquals(4, dto.fetchPool().poolSize());
        assertEquals(2, dto.fetchPool().queueSize());
    }
}
