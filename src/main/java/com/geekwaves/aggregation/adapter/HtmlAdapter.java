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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class HtmlAdapter implements SourceAdapter {
    static final Duration FETCH_TIMEOUT = Duration.ofSeconds(15);
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
            String timeText = timeSel.isEmpty() ? "" : (row.selectFirst(timeSel) == null ? "" : row.selectFirst(timeSel).text());
            items.add(new FetchedItem(
                    Integer.toHexString((title + url).hashCode()),
                    title, url,
                    row.text().replace(title, "").trim(),
                    row.text(),
                    "", "", "{}", 0,
                    parseTime(timeText)));
        }
        return items;
    }

    private LocalDateTime parseTime(String text) {
        if (text == null || text.isBlank()) return LocalDateTime.now();
        return LocalDateTime.now().minusHours(1);
    }
}
