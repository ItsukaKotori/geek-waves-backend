package com.geekwaves.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
public class AiController {
    private static final MediaType SSE_UTF8 = new MediaType(MediaType.TEXT_EVENT_STREAM, StandardCharsets.UTF_8);

    private final AiService aiService;

    @PostMapping(value = "/api/ai/analyze/{newsId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> analyze(@PathVariable Long newsId,
                                              @RequestParam(defaultValue = "false") boolean force) {
        SseEmitter emitter = new SseEmitter(120_000L);
        aiService.analyze(newsId, force, clientIp())
                .subscribe(chunk -> {
                    try {
                        if (chunk.done()) {
                            emitter.send(SseEmitter.event().data("__DONE__"));
                            emitter.complete();
                        } else {
                            emitter.send(SseEmitter.event().data(chunk.text()));
                        }
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                }, emitter::completeWithError, emitter::complete);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(SSE_UTF8);
        return new ResponseEntity<>(emitter, headers, HttpStatus.OK);
    }

    @PostMapping("/api/news/{id}/refresh-ai")
    public SseEmitter refreshAi(@PathVariable Long id) {
        return analyze(id, true).getBody();
    }

    private String clientIp() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes s ? s.getRequest().getRemoteAddr() : null;
    }
}
