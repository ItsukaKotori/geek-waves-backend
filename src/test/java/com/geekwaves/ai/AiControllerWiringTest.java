package com.geekwaves.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiControllerWiringTest {

    private AiService aiService;
    private AiController controller;

    @BeforeEach
    void setUp() {
        aiService = mock(AiService.class);
        controller = new AiController(aiService);
    }

    private record CapturedHooks(SseEmitter emitter,
                                 AtomicReference<Runnable> timeoutHook,
                                 AtomicReference<Consumer<Throwable>> errorHook,
                                 AtomicReference<Runnable> completionHook) {
    }

    private CapturedHooks captureLifecycleRegistrations() {
        SseEmitter emitter = spy(new SseEmitter(180_000L));
        AtomicReference<Runnable> timeoutHook = new AtomicReference<>();
        AtomicReference<Consumer<Throwable>> errorHook = new AtomicReference<>();
        AtomicReference<Runnable> completionHook = new AtomicReference<>();
        doAnswer(inv -> {
            timeoutHook.set(inv.getArgument(0));
            return null;
        }).when(emitter).onTimeout(any());
        doAnswer(inv -> {
            errorHook.set(inv.getArgument(0));
            return null;
        }).when(emitter).onError(any());
        doAnswer(inv -> {
            completionHook.set(inv.getArgument(0));
            return null;
        }).when(emitter).onCompletion(any());
        return new CapturedHooks(emitter, timeoutHook, errorHook, completionHook);
    }

    @Test
    void emitterTimeoutIs180Seconds() {
        when(aiService.analyze(anyLong(), anyBoolean(), any()))
                .thenReturn(Flux.never());

        ResponseEntity<SseEmitter> response = controller.analyze(42L, false);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(180_000L, response.getBody().getTimeout(), "流式超时应为 180 秒");
    }

    @Test
    void timeoutCallbackDisposesUpstream() {
        CapturedHooks hooks = captureLifecycleRegistrations();

        Disposable upstream = controller.streamTo(Flux.never(), hooks.emitter());

        assertFalse(upstream.isDisposed());
        hooks.timeoutHook().get().run();
        assertTrue(upstream.isDisposed(), "超时后应取消上游订阅,避免白烧 token");
    }

    @Test
    void errorCallbackDisposesUpstream() {
        CapturedHooks hooks = captureLifecycleRegistrations();

        Disposable upstream = controller.streamTo(Flux.never(), hooks.emitter());

        assertFalse(upstream.isDisposed());
        hooks.errorHook().get().accept(new IllegalStateException("async error"));
        assertTrue(upstream.isDisposed(), "错误时也应取消上游订阅");
    }

    @Test
    void completionCallbackDisposesUpstream() {
        CapturedHooks hooks = captureLifecycleRegistrations();

        Disposable upstream = controller.streamTo(Flux.never(), hooks.emitter());

        assertFalse(upstream.isDisposed());
        hooks.completionHook().get().run();
        assertTrue(upstream.isDisposed(), "完成时同样要释放上游订阅句柄");
    }

    @Test
    void sendFailureCancelsUpstreamImmediatelyAndCompletesWithError() throws IOException {
        SseEmitter emitter = spy(new SseEmitter(180_000L));
        IOException brokenPipe = new IOException("broken pipe");
        doThrow(brokenPipe).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        Disposable upstream = controller.streamTo(Flux.just(new AiChunk("片段", false)), emitter);

        verify(emitter).completeWithError(brokenPipe);
        assertTrue(upstream.isDisposed(), "客户端断连导致发送失败时,应立即取消上游以停止生成");
    }

    @Test
    void doneChunkSendsDoneMarkerAndCompletesNormally() throws IOException {
        SseEmitter emitter = spy(new SseEmitter(180_000L));

        controller.streamTo(Flux.just(new AiChunk("", true)), emitter);

        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter, atLeastOnce()).complete();
        verify(emitter, never()).completeWithError(any(Throwable.class));
    }
}
