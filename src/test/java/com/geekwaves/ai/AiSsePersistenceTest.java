package com.geekwaves.ai;

import com.geekwaves.aggregation.domain.NewsItem;
import com.geekwaves.aggregation.domain.mapper.NewsItemMapper;
import com.geekwaves.ai.domain.mapper.AiProviderMapper;
import com.geekwaves.config.CryptoService;
import com.geekwaves.config.port.RateLimitPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiSsePersistenceTest {

    private AiProviderMapper providerMapper;
    private NewsItemMapper newsItemMapper;
    private CryptoService cryptoService;
    private RateLimitPort rateLimitPort;
    private AiProviderRegistry registry;
    private AiProvider streamProvider;
    private AiService service;
    private AiController controller;

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
        controller = new AiController(service);

        news = new NewsItem();
        news.setId(42L);
        news.setTitle("Kotlin 2.2 发布");
        news.setSummary("K2 编译器默认开启");
        news.setContent("正文内容");
        when(newsItemMapper.selectById(42L)).thenReturn(news);
        when(rateLimitPort.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(true);

        com.geekwaves.ai.domain.AiProvider p = new com.geekwaves.ai.domain.AiProvider();
        p.setId(1L);
        p.setVendor("OPENAI_COMPAT");
        p.setBaseUrl("http://ai.local");
        p.setModel("m1");
        p.setEnabled(true);
        p.setIsDefault(true);
        when(providerMapper.selectList(any())).thenReturn(List.of(p));
        when(registry.match("OPENAI_COMPAT")).thenReturn(Optional.of(streamProvider));
    }

    @Test
    void emitterCompletionAfterDoneChunkStillPersistsDone() throws Exception {
        Sinks.Many<AiChunk> sink = Sinks.many().unicast().onBackpressureBuffer();
        when(streamProvider.streamChat(any(), anyList())).thenReturn(sink.asFlux());

        SseEmitter emitter = spy(new SseEmitter(180_000L));
        AtomicReference<Runnable> completionHook = new AtomicReference<>();
        doAnswer(inv -> {
            completionHook.set(inv.getArgument(0));
            return null;
        }).when(emitter).onCompletion(any());

        CountDownLatch firstSend = new CountDownLatch(1);
        CountDownLatch controllerCompleted = new CountDownLatch(1);
        AtomicInteger sends = new AtomicInteger();
        doAnswer(inv -> {
            if (sends.incrementAndGet() == 1) {
                firstSend.countDown();
            }
            return null;
        }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        doAnswer(inv -> {
            controllerCompleted.countDown();
            return null;
        }).when(emitter).complete();

        controller.streamTo(service.analyze(42L, true, "203.0.113.7"), emitter);

        sink.tryEmitNext(new AiChunk("增量", false));
        assertTrue(firstSend.await(2, TimeUnit.SECONDS), "普通分块应即时推送");

        sink.tryEmitNext(new AiChunk("", true));
        assertTrue(controllerCompleted.await(2, TimeUnit.SECONDS), "done 分块应触发 __DONE__ 并 complete");

        completionHook.get().run();
        sink.tryEmitComplete();

        ArgumentCaptor<NewsItem> captor = ArgumentCaptor.forClass(NewsItem.class);
        verify(newsItemMapper, times(2)).updateById(captor.capture());
        List<NewsItem> updates = captor.getAllValues();
        assertEquals("PENDING", updates.get(0).getAiStatus());
        assertEquals("DONE", updates.get(1).getAiStatus(), "emitter 完成回调的 dispose 竞态不得丢失 DONE 落库");
        assertEquals("增量", updates.get(1).getAiSummary());
    }
}
