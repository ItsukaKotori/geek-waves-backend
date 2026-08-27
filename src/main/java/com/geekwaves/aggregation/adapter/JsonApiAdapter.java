package com.geekwaves.aggregation.adapter;

import com.geekwaves.aggregation.domain.InfoSource;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JsonApiAdapter implements SourceAdapter {
    static final Duration GRACE = Duration.ofMinutes(10);
    static final Configuration CONFIG = Configuration.defaultConfiguration();
    static final String DEFAULT_ITEMS = "$[*]";
    static final String DEFAULT_TITLE = "$.title";
    static final String DEFAULT_URL = "$.url";
    static final String DEFAULT_TIME = "$.created_time";
    static final String DEFAULT_SUMMARY = "$.content";
    static final String DEFAULT_AUTHOR = "$.member.username";
    static final String DEFAULT_ID = "$.id";
    static final String DEFAULT_SCORE = "$.votes";
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Override
    public SourceType type() {
        return SourceType.JSON_API;
    }

    @Override
    public List<FetchedItem> fetch(InfoSource source, Instant since) throws Exception {
        var config = objectMapper.readValue(source.getConfigJson() == null || source.getConfigJson().isBlank()
                ? "{}" : source.getConfigJson(), JsonNode.class);
        String json = webClientBuilder.build().get().uri(source.getBaseUrl())
                .retrieve().bodyToMono(String.class).block();
        return extract(json, config, since);
    }

    List<FetchedItem> extract(String json, JsonNode config, Instant since) {
        Object rowsObj = JsonPath.using(CONFIG).parse(json).read(config.path("itemsPath").asText(DEFAULT_ITEMS));
        List<Map<String, Object>> rows = normalizeRows(rowsObj);
        List<FetchedItem> items = new ArrayList<>();
        LocalDateTime threshold = since == null ? null
                : LocalDateTime.ofInstant(since.minus(GRACE), ZoneId.systemDefault());
        for (Map<String, Object> row : rows) {
            Long publishedMs = extractTime(row, config);
            if (publishedMs == null) continue;
            LocalDateTime published = LocalDateTime.ofInstant(Instant.ofEpochMilli(publishedMs), ZoneId.systemDefault());
            if (threshold != null && published.isBefore(threshold)) continue;
            items.add(new FetchedItem(
                    strValue(read(row, config.path("idPath").asText(DEFAULT_ID))),
                    strValue(read(row, config.path("titlePath").asText(DEFAULT_TITLE))),
                    strValue(read(row, config.path("urlPath").asText(DEFAULT_URL))),
                    strValue(read(row, config.path("summaryPath").asText(DEFAULT_SUMMARY))),
                    "",
                    strValue(read(row, config.path("authorPath").asText(DEFAULT_AUTHOR))),
                    "",
                    "{}",
                    score(row, config),
                    published));
        }
        return items;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeRows(Object rowsObj) {
        if (rowsObj instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object row : list) {
                if (row instanceof Map<?, ?> map) rows.add((Map<String, Object>) map);
            }
            return rows;
        }
        if (rowsObj instanceof Map<?, ?> map) {
            return List.of((Map<String, Object>) map);
        }
        return List.of();
    }

    private Object read(Map<String, Object> row, String path) {
        try {
            return JsonPath.using(CONFIG).parse(row).read(path);
        } catch (RuntimeException e) {
            log.debug("json-path miss, treat as null: path={}", path, e);
            return null;
        }
    }

    private Long extractTime(Map<String, Object> row, JsonNode config) {
        Object value = read(row, config.path("timePath").asText(DEFAULT_TIME));
        if (value == null) return null;
        long raw;
        if (value instanceof Number number) {
            raw = number.longValue();
        } else {
            try {
                raw = Long.parseLong(String.valueOf(value).trim());
            } catch (RuntimeException e) {
                log.debug("time unparsable, item skipped: value={}", value, e);
                return null;
            }
        }
        if (raw <= 0) return null;
        return raw < 1_000_000_000_000L ? raw * 1000L : raw;
    }

    private int score(Map<String, Object> row, JsonNode config) {
        Object value = read(row, config.path("scorePath").asText(DEFAULT_SCORE));
        if (value instanceof Number number) return number.intValue();
        if (value == null) return 0;
        try {
            return (int) Double.parseDouble(String.valueOf(value).trim());
        } catch (RuntimeException e) {
            log.debug("score unparsable, keep 0: value={}", value, e);
            return 0;
        }
    }

    private String strValue(Object value) {
        if (value == null) return "";
        if (value instanceof String string) return string;
        return String.valueOf(value);
    }
}
