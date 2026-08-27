package com.geekwaves.ai;

import com.geekwaves.aggregation.domain.NewsItem;
import com.geekwaves.aggregation.domain.mapper.NewsItemMapper;
import com.geekwaves.ai.domain.mapper.AiProviderMapper;
import com.geekwaves.config.CryptoService;
import com.geekwaves.config.port.RateLimitPort;
import org.itsuka.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiServiceTest {

    private AiProviderMapper providerMapper;
    private NewsItemMapper newsItemMapper;
    private CryptoService cryptoService;
    private RateLimitPort rateLimitPort;
    private AiProviderRegistry registry;
    private AiProvider streamProvider;
    private AiService service;

    private NewsItem news;

    @BeforeEach
    void setUp() {
        providerMapper = mock(AiProviderMapper.class);
        newsItemMapper = mock(NewsItemMapper.class);
        cryptoService = mock(CryptoService.class);
        rateLimitPort = mock(RateLimitPort.class);
        registry = mock(AiProviderRegistry.class);
        streamProvider = mock(AiProvider.class);
        service = new AiService(registry, providerMapper, newsItemMapper, cryptoService, rateLimitPort);

        news = new NewsItem();
        news.setId(42L);
        news.setTitle("Kotlin 2.2 发布");
        news.setSummary("K2 编译器默认开启");
        news.setContent("正文内容");
        when(newsItemMapper.selectById(42L)).thenReturn(news);
        when(rateLimitPort.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(cryptoService.decrypt("ENC")).thenReturn("dec-key");
    }

    private com.geekwaves.ai.domain.AiProvider domainProvider() {
        com.geekwaves.ai.domain.AiProvider p = new com.geekwaves.ai.domain.AiProvider();
        p.setId(1L);
        p.setVendor("OPENAI_COMPAT");
        p.setBaseUrl("http://ai.local");
        p.setApiKeyEnc("ENC");
        p.setModel("m1");
        p.setEnabled(true);
        p.setIsDefault(true);
        return p;
    }

    @Test
    void cacheHitReturnsStoredSummaryWithoutTouchingProviders() {
        news.setAiStatus("DONE");
        news.setAiSummary("既有解读");
        List<AiChunk> chunks = service.analyze(42L, false, "203.0.113.7").collectList().block();
        assertEquals(1, chunks.size());
        AiChunk chunk = chunks.get(0);
        assertEquals("既有解读", chunk.text());
        assertTrue(chunk.done());
        verify(providerMapper, never()).selectList(any());
        verify(rateLimitPort, never()).tryAcquire(anyString(), anyInt(), any());
        verifyNoInteractions(registry);
        verify(newsItemMapper, never()).updateById(any(NewsItem.class));
    }

    @Test
    void forceAnalyzeStreamsProviderAndPersistsJoinedSummary() {
        news.setAiStatus("DONE");
        news.setAiSummary("旧解读");
        when(providerMapper.selectList(any())).thenReturn(List.of(domainProvider()));
        when(registry.match("OPENAI_COMPAT")).thenReturn(Optional.of(streamProvider));
        when(streamProvider.streamChat(any(), anyList())).thenReturn(
                Flux.just(new AiChunk("要点", false), new AiChunk("已解读", true)));

        List<AiChunk> chunks = service.analyze(42L, true, "203.0.113.7").collectList().block();

        assertEquals(List.of("要点", "已解读"), chunks.stream().map(AiChunk::text).toList());

        ArgumentCaptor<NewsItem> captor = ArgumentCaptor.forClass(NewsItem.class);
        verify(newsItemMapper, times(2)).updateById(captor.capture());
        List<NewsItem> updates = captor.getAllValues();
        assertEquals("PENDING", updates.get(0).getAiStatus());
        assertNull(updates.get(0).getAiSummary());
        assertEquals("DONE", updates.get(1).getAiStatus());
        assertEquals("要点已解读", updates.get(1).getAiSummary());
        assertNotNull(updates.get(1).getAiSummaryAt());

        service.analyze(42L, true, "203.0.113.7").collectList().block();
        verify(streamProvider, times(2)).streamChat(any(), anyList());
    }

    @Test
    void streamsEachChunkIncrementallyBeforeProviderCompletes() throws Exception {
        when(providerMapper.selectList(any())).thenReturn(List.of(domainProvider()));
        when(registry.match("OPENAI_COMPAT")).thenReturn(Optional.of(streamProvider));
        Sinks.Many<AiChunk> sink = Sinks.many().unicast().onBackpressureBuffer();
        when(streamProvider.streamChat(any(), anyList())).thenReturn(sink.asFlux());

        CountDownLatch firstChunk = new CountDownLatch(1);
        CountDownLatch terminated = new CountDownLatch(1);
        AtomicInteger received = new AtomicInteger();
        service.analyze(42L, true, "203.0.113.7")
                .subscribe(chunk -> {
                    received.incrementAndGet();
                    firstChunk.countDown();
                }, e -> terminated.countDown(), terminated::countDown);

        sink.tryEmitNext(new AiChunk("增量", false));
        assertTrue(firstChunk.await(2, TimeUnit.SECONDS),
                "第一个 chunk 应在上游未完成时即时转发,而不是等 collectList 缓存");
        assertEquals(1, received.get());

        sink.tryEmitNext(new AiChunk("推送", false));
        sink.tryEmitComplete();
        assertTrue(terminated.await(2, TimeUnit.SECONDS), "上游完成后订阅应正常终止");
        assertEquals(2, received.get());
    }

    @Test
    void cancelledSubscriptionReleasesConcurrencySlot() {
        when(providerMapper.selectList(any())).thenReturn(List.of(domainProvider()));
        when(registry.match("OPENAI_COMPAT")).thenReturn(Optional.of(streamProvider));
        Sinks.Many<AiChunk> sink = Sinks.many().unicast().onBackpressureBuffer();
        when(streamProvider.streamChat(any(), anyList())).thenReturn(sink.asFlux());

        Disposable disposable = service.analyze(42L, true, "203.0.113.7").subscribe();
        disposable.dispose();

        assertDoesNotThrow(() -> service.analyze(42L, true, "203.0.113.7"),
                "取消订阅后并发闸门应释放,否则断连一次就永久卡死 AI 解读");
    }

    @Test
    void doneMarkerPersistsExactlyOnceDespiteNaturalCompletion() {
        when(providerMapper.selectList(any())).thenReturn(List.of(domainProvider()));
        when(registry.match("OPENAI_COMPAT")).thenReturn(Optional.of(streamProvider));
        when(streamProvider.streamChat(any(), anyList())).thenReturn(
                Flux.just(new AiChunk("要点", false), new AiChunk("", true)));

        service.analyze(42L, true, "203.0.113.7").collectList().block();

        ArgumentCaptor<NewsItem> captor = ArgumentCaptor.forClass(NewsItem.class);
        verify(newsItemMapper, times(2)).updateById(captor.capture());
        List<NewsItem> updates = captor.getAllValues();
        assertEquals("PENDING", updates.get(0).getAiStatus());
        assertEquals("DONE", updates.get(1).getAiStatus());
        assertEquals("要点", updates.get(1).getAiSummary());
    }

    @Test
    void rateLimitExceededThrowsTooManyRequests() {
        when(rateLimitPort.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(false);
        ServiceException e = assertThrows(ServiceException.class, () -> service.analyze(42L, true, null));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatus());
        verify(rateLimitPort).tryAcquire("rate:ai:analyze:unknown", 5, Duration.ofMinutes(10));
    }

    @Test
    void concurrentStreamingCallThrowsTooManyRequests() {
        when(providerMapper.selectList(any())).thenReturn(List.of(domainProvider()));
        when(registry.match("OPENAI_COMPAT")).thenReturn(Optional.of(streamProvider));
        when(streamProvider.streamChat(any(), anyList())).thenReturn(Flux.never());

        service.analyze(42L, true, "203.0.113.7");
        ServiceException e = assertThrows(ServiceException.class, () -> service.analyze(42L, true, "203.0.113.7"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatus());
    }

    @Test
    void providerErrorMarksFailed() {
        when(providerMapper.selectList(any())).thenReturn(List.of(domainProvider()));
        when(registry.match("OPENAI_COMPAT")).thenReturn(Optional.of(streamProvider));
        when(streamProvider.streamChat(any(), anyList())).thenReturn(
                Flux.error(new IllegalStateException("服务不可用")));

        assertThrows(IllegalStateException.class,
                () -> service.analyze(42L, true, "203.0.113.7").collectList().block());

        ArgumentCaptor<NewsItem> captor = ArgumentCaptor.forClass(NewsItem.class);
        verify(newsItemMapper, times(2)).updateById(captor.capture());
        List<NewsItem> updates = captor.getAllValues();
        assertEquals("PENDING", updates.get(0).getAiStatus());
        assertEquals("FAILED", updates.get(1).getAiStatus());
        assertEquals("服务不可用", updates.get(1).getAiSummary());
    }

    @Test
    void noDefaultProviderThrowsBadRequestAndLeavesGateOpen() {
        when(providerMapper.selectList(any())).thenReturn(List.of());

        ServiceException first = assertThrows(ServiceException.class, () -> service.analyze(42L, true, "ip"));
        assertEquals(HttpStatus.BAD_REQUEST, first.getStatus());
        ServiceException second = assertThrows(ServiceException.class, () -> service.analyze(42L, true, "ip"));
        assertEquals(HttpStatus.BAD_REQUEST, second.getStatus());
    }

    @Test
    void missingNewsThrowsNotFound() {
        when(newsItemMapper.selectById(42L)).thenReturn(null);
        ServiceException e = assertThrows(ServiceException.class, () -> service.analyze(42L, true, "ip"));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void buildsProviderConfigAndTruncatesUserContent() {
        news.setContent("X".repeat(7000));
        when(providerMapper.selectList(any())).thenReturn(List.of(domainProvider()));
        when(registry.match("OPENAI_COMPAT")).thenReturn(Optional.of(streamProvider));
        when(streamProvider.streamChat(any(), anyList())).thenReturn(Flux.just(new AiChunk("ok", true)));

        service.analyze(42L, true, "203.0.113.7").collectList().block();

        ArgumentCaptor<AiProviderConfig> cfgCaptor = ArgumentCaptor.forClass(AiProviderConfig.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiMessage>> msgCaptor = ArgumentCaptor.forClass(List.class);
        verify(streamProvider).streamChat(cfgCaptor.capture(), msgCaptor.capture());
        assertEquals("http://ai.local", cfgCaptor.getValue().baseUrl());
        assertEquals("dec-key", cfgCaptor.getValue().apiKey());
        assertEquals("m1", cfgCaptor.getValue().model());
        List<AiMessage> messages = msgCaptor.getValue();
        assertEquals("system", messages.get(0).role());
        assertEquals(AiPrompt.SYSTEM, messages.get(0).content());
        assertEquals("user", messages.get(1).role());
        assertTrue(messages.get(1).content().startsWith("请解读以下技术资讯:\n"));
        assertEquals(6011, messages.get(1).content().length());
    }
}
