package com.geekwaves.tool.service;

import com.geekwaves.tool.dto.HttpRequestCommand;
import com.geekwaves.tool.dto.HttpRequestResult;
import com.geekwaves.tool.security.SsrFGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class HttpTestService {
    private final WebClient.Builder webClientBuilder;

    public Mono<HttpRequestResult> send(HttpRequestCommand cmd) {
        try {
            SsrFGuard.assertSafe(cmd.getUrl());
        } catch (RuntimeException e) {
            log.info("tool.http-request blocked method={} host={} reason={}",
                    cmd.getMethod(), hostOf(cmd.getUrl()), e.getMessage());
            throw e;
        }
        HttpMethod httpMethod = HttpMethod.valueOf(cmd.getMethod().toUpperCase());
        String logTarget = schemeHostPath(cmd.getUrl());
        long start = System.currentTimeMillis();
        WebClient client = webClientBuilder
                .clone()
                .defaultHeader("User-Agent", "GeekWaves-Tool/0.1")
                .build();
        boolean hasBody = cmd.getBody() != null && !cmd.getBody().isEmpty() && !httpMethod.equals(HttpMethod.GET);
        WebClient.RequestBodySpec spec = client.method(httpMethod)
                .uri(URI.create(cmd.getUrl()))
                .headers(h -> { if (cmd.getHeaders() != null) cmd.getHeaders().forEach(h::add); });
        WebClient.RequestHeadersSpec<?> request = hasBody ? spec.bodyValue(cmd.getBody()) : spec;
        return request.exchangeToMono(resp -> resp.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> {
                            Map<String, String> headers = new HashMap<>();
                            resp.headers().asHttpHeaders().forEach((k, v) -> headers.put(k, String.join(", ", v)));
                            int status = resp.statusCode().value();
                            long tookMs = System.currentTimeMillis() - start;
                            log.info("tool.http-request method={} url={} status={} tookMs={}",
                                    cmd.getMethod(), logTarget, status, tookMs);
                            return HttpRequestResult.builder()
                                    .status(status)
                                    .tookMs(tookMs)
                                    .headers(headers)
                                    .body(body.length() > 1_000_000 ? body.substring(0, 1_000_000) : body)
                                    .build();
                        }))
                .timeout(Duration.ofMillis(cmd.getTimeoutMs() == null ? 15_000 : cmd.getTimeoutMs()))
                .onErrorResume(e -> {
                    long tookMs = System.currentTimeMillis() - start;
                    log.info("tool.http-request method={} url={} status=-1 tookMs={} error={}",
                            cmd.getMethod(), logTarget, tookMs, e.getClass().getSimpleName());
                    return Mono.just(HttpRequestResult.builder()
                            .status(-1).tookMs(tookMs)
                            .headers(Map.of()).body("错误: " + e.getMessage()).build());
                });
    }

    static String hostOf(String rawUrl) {
        try {
            return new URI(rawUrl).getHost();
        } catch (URISyntaxException e) {
            return "invalid-url";
        }
    }

    static String schemeHostPath(String rawUrl) {
        try {
            URI uri = new URI(rawUrl);
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                path = "/";
            }
            return uri.getScheme() + "://" + uri.getHost() + path;
        } catch (URISyntaxException e) {
            return "invalid-url";
        }
    }
}
