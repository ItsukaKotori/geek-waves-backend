package com.geekwaves.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.geekwaves.aggregation.domain.NewsItem;
import com.geekwaves.aggregation.domain.mapper.NewsItemMapper;
import com.geekwaves.ai.domain.mapper.AiProviderMapper;
import com.geekwaves.config.CryptoService;
import com.geekwaves.config.port.RateLimitPort;
import lombok.RequiredArgsConstructor;
import org.itsuka.core.exception.ServiceException;
import org.jsoup.Jsoup;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class AiService {
    private static final int MAX_CONCURRENCY = 1;

    private final AiProviderRegistry registry;
    private final AiProviderMapper providerMapper;
    private final NewsItemMapper newsItemMapper;
    private final CryptoService cryptoService;
    private final RateLimitPort rateLimitPort;
    private final AtomicInteger concurrency = new AtomicInteger(0);

    public Flux<AiChunk> analyze(Long newsId, boolean force, String clientIp) {
        NewsItem news = newsItemMapper.selectById(newsId);
        if (news == null) {
            throw ServiceException.create(HttpStatus.NOT_FOUND, "资讯不存在");
        }
        if ("DONE".equals(news.getAiStatus()) && !force && news.getAiSummary() != null) {
            return Flux.just(new AiChunk(news.getAiSummary(), true));
        }
        if (!rateLimitPort.tryAcquire("rate:ai:analyze:" + safeIp(clientIp), 5, Duration.ofMinutes(10))) {
            throw ServiceException.create(HttpStatus.TOO_MANY_REQUESTS, "AI 解读触发过于频繁(10 分钟内 5 次),请稍后再试");
        }
        var provider = defaultProvider();
        if (concurrency.incrementAndGet() > MAX_CONCURRENCY) {
            concurrency.decrementAndGet();
            throw ServiceException.create(HttpStatus.TOO_MANY_REQUESTS, "已有 AI 解读进行中,请稍候");
        }
        return streamInto(provider, news)
                .doOnError(e -> markFailed(news.getId(), e.getMessage()))
                .doFinally(signal -> concurrency.decrementAndGet());
    }

    private Flux<AiChunk> streamInto(com.geekwaves.ai.domain.AiProvider provider, NewsItem news) {
        markPending(news.getId());
        List<AiMessage> messages = List.of(
                new AiMessage("system", AiPrompt.SYSTEM),
                new AiMessage("user", userContent(news)));
        var cfg = new AiProviderConfig(provider.getBaseUrl(),
                provider.getApiKeyEnc() == null ? null : cryptoService.decrypt(provider.getApiKeyEnc()),
                provider.getModel());
        StringBuilder full = new StringBuilder();
        AtomicBoolean persisted = new AtomicBoolean(false);
        return registry.match(provider.getVendor()).orElseThrow()
                .streamChat(cfg, messages)
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> {
                    full.append(chunk.text() == null ? "" : chunk.text());
                    if (chunk.done() && persisted.compareAndSet(false, true)) {
                        markDone(news.getId(), full.toString());
                    }
                })
                .doOnComplete(() -> {
                    if (persisted.compareAndSet(false, true)) {
                        markDone(news.getId(), full.toString());
                    }
                });
    }

    private com.geekwaves.ai.domain.AiProvider defaultProvider() {
        var list = providerMapper.selectList(new QueryWrapper<com.geekwaves.ai.domain.AiProvider>()
                .eq("enabled", 1).eq("is_default", 1));
        if (list.isEmpty()) {
            throw ServiceException.create(HttpStatus.BAD_REQUEST, "请先配置 AI 厂商(默认启用)");
        }
        return list.get(0);
    }

    private String userContent(NewsItem n) {
        String content = n.getContent();
        if (content != null && content.contains("<")) {
            content = Jsoup.parse(content).text();
        }
        String base = n.getTitle() + "\n" + (n.getSummary() == null ? "" : n.getSummary())
                + "\n" + (content == null ? "" : content);
        if (base.length() > 6000) {
            base = base.substring(0, 6000);
        }
        return "请解读以下技术资讯:\n" + base;
    }

    private String safeIp(String ip) {
        return ip == null ? "unknown" : ip;
    }

    private void markPending(Long id) {
        updateAi(id, "PENDING", null);
    }

    private void markDone(Long id, String summary) {
        updateAi(id, "DONE", summary);
    }

    private void markFailed(Long id, String err) {
        updateAi(id, "FAILED", err == null ? "error" : err.substring(0, Math.min(err.length(), 200)));
    }

    private void updateAi(Long id, String status, String summary) {
        NewsItem n = new NewsItem();
        n.setId(id);
        n.setAiStatus(status);
        if (summary != null) {
            n.setAiSummary(summary);
            n.setAiSummaryAt(LocalDateTime.now());
        }
        newsItemMapper.updateById(n);
    }
}
