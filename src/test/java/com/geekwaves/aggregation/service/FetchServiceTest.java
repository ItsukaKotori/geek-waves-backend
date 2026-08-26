package com.geekwaves.aggregation.service;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.geekwaves.aggregation.adapter.AdapterRegistry;
import com.geekwaves.aggregation.adapter.FetchedItem;
import com.geekwaves.aggregation.adapter.SourceAdapter;
import com.geekwaves.aggregation.domain.InfoSource;
import com.geekwaves.aggregation.domain.mapper.InfoSourceMapper;
import com.geekwaves.config.port.CachePort;
import com.geekwaves.config.port.LockPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FetchServiceTest {
    private AdapterRegistry registry;
    private InfoSourceMapper sourceMapper;
    private NewsService newsService;
    private CachePort cachePort;
    private LockPort lockPort;
    private FetchService service;

    @BeforeEach
    void setUp() {
        registry = mock(AdapterRegistry.class);
        sourceMapper = mock(InfoSourceMapper.class);
        newsService = mock(NewsService.class);
        cachePort = mock(CachePort.class);
        lockPort = mock(LockPort.class);
        service = new FetchService(registry, sourceMapper, newsService, cachePort, lockPort);
    }

    @Test
    void returnsFalseWhenLockBusy() {
        InfoSource source = source(1L, "RSS");
        when(lockPort.tryLock("fetch:lock:1", FetchService.LOCK_TTL)).thenReturn(false);
        assertFalse(service.fetchNow(source));
        verifyNoInteractions(registry, sourceMapper, newsService, cachePort);
        verify(lockPort, never()).unlock(anyString());
    }

    @Test
    void marksSkippedAndReturnsFalseWhenNoAdapterMatches() {
        InfoSource source = source(1L, "RSS");
        when(lockPort.tryLock("fetch:lock:1", FetchService.LOCK_TTL)).thenReturn(true);
        when(registry.match("RSS")).thenReturn(Optional.empty());
        assertFalse(service.fetchNow(source));
        assertEquals("SKIPPED", source.getLastFetchStatus());
        assertEquals("无适配器: RSS", source.getLastError());
        verify(sourceMapper).updateById(source);
        verify(lockPort).unlock("fetch:lock:1");
    }

    @Test
    void successPersistsInvalidatesCacheAndClearsErrorViaWrapper() throws Exception {
        InfoSource source = source(1L, "GITHUB_API");
        source.setLastError("old error");
        source.setFailCount(2);
        SourceAdapter adapter = mock(SourceAdapter.class);
        FetchedItem item = new FetchedItem("i1", "t", "https://github.com/x/y", "", "", "", "", "{}", 0, LocalDateTime.now());
        when(lockPort.tryLock("fetch:lock:1", FetchService.LOCK_TTL)).thenReturn(true);
        when(registry.match("GITHUB_API")).thenReturn(Optional.of(adapter));
        when(adapter.fetch(eq(source), any())).thenReturn(List.of(item));
        when(newsService.persist(eq(source), anyList())).thenReturn(1);

        assertTrue(service.fetchNow(source));

        assertEquals("SUCCESS", source.getLastFetchStatus());
        assertEquals(0, source.getFailCount());
        ArgumentCaptor<UpdateWrapper<InfoSource>> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(sourceMapper).update(isNull(), captor.capture());
        UpdateWrapper<InfoSource> wrapper = captor.getValue();
        String sqlSet = wrapper.getSqlSet();
        assertTrue(sqlSet.contains("last_fetch_at"));
        assertTrue(sqlSet.contains("last_fetch_status"));
        assertTrue(sqlSet.contains("last_error"));
        assertTrue(sqlSet.contains("fail_count"));
        Map<String, Object> params = paramsOf(wrapper);
        assertTrue(params.containsValue(null));
        assertTrue(params.containsValue("SUCCESS"));
        assertTrue(params.containsValue(0));
        verify(cachePort).invalidateByPrefix("news:list:");
        verify(lockPort).unlock("fetch:lock:1");
    }

    @Test
    void failureMarksFailedIncrementsCountAndDoesNotThrow() throws Exception {
        InfoSource source = source(1L, "GITHUB_API");
        source.setFailCount(2);
        SourceAdapter adapter = mock(SourceAdapter.class);
        when(lockPort.tryLock("fetch:lock:1", FetchService.LOCK_TTL)).thenReturn(true);
        when(registry.match("GITHUB_API")).thenReturn(Optional.of(adapter));
        when(adapter.fetch(eq(source), any())).thenThrow(new RuntimeException("boom"));

        assertFalse(service.fetchNow(source));

        assertEquals("FAILED", source.getLastFetchStatus());
        assertEquals("boom", source.getLastError());
        assertEquals(3, source.getFailCount());
        verify(sourceMapper).updateById(source);
        verify(lockPort).unlock("fetch:lock:1");
    }

    @Test
    void isDueFalseWhenDisabled() {
        InfoSource source = source(1L, "RSS");
        source.setEnabled(false);
        assertFalse(service.isDue(source));
    }

    @Test
    void isDueTrueWhenNeverFetched() {
        InfoSource source = source(1L, "RSS");
        source.setEnabled(true);
        assertTrue(service.isDue(source));
    }

    @Test
    void isDueTrueAfterIntervalElapsed() {
        InfoSource source = source(1L, "RSS");
        source.setEnabled(true);
        source.setRefreshMinutes(30);
        source.setFailCount(0);
        source.setLastFetchAt(LocalDateTime.now().minusMinutes(31));
        assertTrue(service.isDue(source));
    }

    @Test
    void isDueFalseWithinInterval() {
        InfoSource source = source(1L, "RSS");
        source.setEnabled(true);
        source.setRefreshMinutes(30);
        source.setFailCount(0);
        source.setLastFetchAt(LocalDateTime.now());
        assertFalse(service.isDue(source));
    }

    @Test
    void isDueScalesIntervalAfterFailures() {
        InfoSource source = source(1L, "RSS");
        source.setEnabled(true);
        source.setRefreshMinutes(60);
        source.setFailCount(5);
        source.setLastFetchAt(LocalDateTime.now().minusMinutes(150));
        assertFalse(service.isDue(source));
        source.setLastFetchAt(LocalDateTime.now().minusMinutes(250));
        assertTrue(service.isDue(source));
    }

    @Test
    void isDueBackoffCappedAtEight() {
        InfoSource source = source(1L, "RSS");
        source.setEnabled(true);
        source.setRefreshMinutes(30);
        source.setFailCount(10);
        source.setLastFetchAt(LocalDateTime.now().minusMinutes(150));
        assertFalse(service.isDue(source));
        source.setLastFetchAt(LocalDateTime.now().minusMinutes(250));
        assertTrue(service.isDue(source));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> paramsOf(UpdateWrapper<InfoSource> wrapper) {
        try {
            Field field = AbstractWrapper.class.getDeclaredField("paramNameValuePairs");
            field.setAccessible(true);
            return (Map<String, Object>) field.get(wrapper);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private InfoSource source(Long id, String type) {
        InfoSource s = new InfoSource();
        s.setId(id);
        s.setCode("src-" + id);
        s.setType(type);
        return s;
    }
}
