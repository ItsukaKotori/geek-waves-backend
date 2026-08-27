package com.geekwaves.aggregation.adapter;

import com.geekwaves.aggregation.domain.InfoSource;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class HtmlAdapter implements SourceAdapter {
    static final Duration FETCH_TIMEOUT = Duration.ofSeconds(15);
    private static final Pattern JUST_NOW = Pattern.compile("(\\bjust\\s+now\\b)|刚刚|刚才", Pattern.CASE_INSENSITIVE);
    private static final Pattern CN_RELATIVE =
            Pattern.compile("(\\d+)\\s*(?:个)?\\s*(秒钟?|分钟?|小时|天|日|周|星期|月|年)\\s*前");
    private static final Pattern EN_RELATIVE =
            Pattern.compile("(\\d+)\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?|days?|weeks?|months?|years?)\\s+ago",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPACT_RELATIVE =
            Pattern.compile("(?<![A-Za-z0-9])(\\d+)\\s*(min|sec|mo|hr|[smhdwy])(?![A-Za-z0-9])",
                    Pattern.CASE_INSENSITIVE);
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    @Override
    public SourceType type() {
        return SourceType.HTML;
    }

    @Override
    public List<FetchedItem> fetch(InfoSource source, Instant since) throws Exception {
        var config = objectMapper.readValue(source.getConfigJson() == null || source.getConfigJson().isBlank()
                ? "{}" : source.getConfigJson(), JsonNode.class);
        String html = webClientBuilder.build().get().uri(URI.create(source.getBaseUrl()))
                .retrieve().bodyToMono(String.class).timeout(FETCH_TIMEOUT).block();
        return parseFromDoc(Jsoup.parse(html == null ? "" : html, source.getBaseUrl()), config);
    }

    public List<FetchedItem> parseFromHtml(String html, JsonNode config) {
        return parseFromDoc(Jsoup.parse(html), config);
    }

    List<FetchedItem> parseFromDoc(Document doc, JsonNode config) {
        String listSel = config.path("listSelector").asText("li");
        String titleSel = config.path("titleSelector").asText("a");
        String urlSel = config.path("urlSelector").asText("a");
        String hrefAttr = config.path("hrefAttr").asText("href");
        String timeSel = config.path("timeSelector").asText("");
        String timeAttr = config.path("timeAttr").asText("");
        String timeFormat = config.path("timeFormat").asText("");
        List<FetchedItem> items = new ArrayList<>();
        for (Element row : doc.select(listSel)) {
            Element titleEl = row.selectFirst(titleSel);
            if (titleEl == null) continue;
            String title = titleEl.text().trim();
            if (title.isEmpty()) continue;
            String url = titleEl.absUrl(hrefAttr);
            if (url.isEmpty()) {
                Element urlEl = row.selectFirst(urlSel);
                url = urlEl == null ? "" : urlEl.absUrl(hrefAttr);
            }
            Element timeEl = timeSel.isEmpty() ? null : row.selectFirst(timeSel);
            String timeValue = timeEl == null ? ""
                    : (timeAttr.isBlank() ? timeEl.text() : timeEl.attr(timeAttr));
            items.add(new FetchedItem(
                    SourceItemIdDigest.sha256First40(title + url),
                    title, url,
                    row.text().replace(title, "").trim(),
                    row.text(),
                    "", "", "{}", 0,
                    parseTime(timeValue, timeFormat)));
        }
        return items;
    }

    private LocalDateTime parseTime(String value, String timeFormat) {
        if (value == null || value.isBlank()) return LocalDateTime.now();
        String text = value.trim();
        LocalDateTime parsed = parseByPattern(text, timeFormat);
        if (parsed == null) parsed = parseIso(text);
        if (parsed == null) parsed = parseRelative(text);
        return parsed == null ? LocalDateTime.now() : parsed;
    }

    private static LocalDateTime parseByPattern(String text, String pattern) {
        if (pattern == null || pattern.isBlank()) return null;
        DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter.ofPattern(pattern.trim());
        } catch (RuntimeException ignored) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, formatter);
        } catch (RuntimeException ignored) {
        }
        try {
            return OffsetDateTime.parse(text, formatter)
                    .atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static LocalDateTime parseIso(String text) {
        try {
            return LocalDateTime.parse(text);
        } catch (RuntimeException ignored) {
        }
        try {
            return OffsetDateTime.parse(text).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (RuntimeException ignored) {
        }
        try {
            return LocalDate.parse(text).atStartOfDay(ZoneId.systemDefault()).toLocalDateTime();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static LocalDateTime parseRelative(String text) {
        try {
            if (JUST_NOW.matcher(text).find()) return LocalDateTime.now();
            var cn = CN_RELATIVE.matcher(text);
            if (cn.find()) return beforeNow(Long.parseLong(cn.group(1)), cn.group(2));
            var en = EN_RELATIVE.matcher(text);
            if (en.find()) return beforeNow(Long.parseLong(en.group(1)), en.group(2));
            var compact = COMPACT_RELATIVE.matcher(text);
            if (compact.find()) return beforeNow(Long.parseLong(compact.group(1)), compact.group(2));
        } catch (ArithmeticException | DateTimeException | IllegalArgumentException ignored) {
        }
        return null;
    }

    static LocalDateTime beforeNow(long amount, String unit) {
        try {
            long seconds = Math.multiplyExact(amount, unitSeconds(unit));
            return LocalDateTime.now().minus(seconds, ChronoUnit.SECONDS);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static long unitSeconds(String unit) {
        return switch (unit.toLowerCase(Locale.ROOT)) {
            case "秒", "秒钟", "second", "seconds", "sec", "secs", "s" -> 1;
            case "分", "分钟", "minute", "minutes", "min", "mins", "m" -> 60;
            case "小时", "hour", "hours", "hr", "hrs", "h" -> 3600;
            case "天", "日", "day", "days", "d" -> 86400;
            case "周", "星期", "week", "weeks", "w" -> 604800;
            case "月", "month", "months", "mo" -> 2592000;
            case "年", "year", "years", "y" -> 31536000;
            default -> throw new IllegalArgumentException("未知时间单位: " + unit);
        };
    }
}
