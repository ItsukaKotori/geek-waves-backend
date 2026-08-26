package com.geekwaves.aggregation.adapter;

import com.geekwaves.aggregation.domain.InfoSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class HnAdapter implements SourceAdapter {
    static final int MAX_IDS = 100;
    static final int MAX_ITEMS = 30;
    static final Duration GRACE = Duration.ofMinutes(10);
    private static final String FALLBACK_URL = "https://news.ycombinator.com/item?id=";
    private final WebClient.Builder webClientBuilder;

    @Override
    public SourceType type() {
        return SourceType.HN_API;
    }

    @Override
    public List<FetchedItem> fetch(InfoSource source, Instant since) throws Exception {
        WebClient client = webClientBuilder.baseUrl(source.getBaseUrl()).build();
        JsonNode ids = client.get().uri("/topstories.json").retrieve().bodyToMono(JsonNode.class).block();
        List<FetchedItem> items = new ArrayList<>();
        if (ids == null) return items;
        int limit = Math.min(MAX_IDS, ids.size());
        for (int i = 0; i < limit && items.size() < MAX_ITEMS; i++) {
            long id = ids.get(i).asLong();
            JsonNode item = client.get().uri("/item/" + id + ".json").retrieve().bodyToMono(JsonNode.class).block();
            if (item == null) continue;
            if (item.path("deleted").asBoolean(false) || item.path("dead").asBoolean(false)) continue;
            if (item.path("type").asString("").equalsIgnoreCase("comment")) continue;
            if (!item.path("title").isString()) continue;
            LocalDateTime published = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(item.path("time").asLong()), ZoneId.systemDefault());
            if (since != null && published.isBefore(LocalDateTime.ofInstant(since.minus(GRACE), ZoneId.systemDefault()))) continue;
            items.add(new FetchedItem(
                    String.valueOf(id),
                    item.path("title").asString(),
                    item.path("url").isString() ? item.path("url").asString() : FALLBACK_URL + id,
                    "",
                    item.path("text").asString(""),
                    item.path("by").asString(""),
                    "",
                    "{}",
                    item.path("score").asInt(0),
                    published));
        }
        return items;
    }
}
