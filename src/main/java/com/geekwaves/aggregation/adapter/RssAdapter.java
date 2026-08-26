package com.geekwaves.aggregation.adapter;

import com.geekwaves.aggregation.domain.InfoSource;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.io.SyndFeedInput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.StringReader;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RssAdapter implements SourceAdapter {
    static final Duration GRACE = Duration.ofMinutes(10);
    private static final int SUMMARY_LIMIT = 2000;
    private static final org.jsoup.safety.Safelist SAFELIST =
            org.jsoup.safety.Safelist.relaxed().addTags("figure", "figcaption");
    private static final String BLOCK_BREAK_TAGS = "address, article, aside, blockquote, details, div, dl, dt, dd, "
            + "figcaption, figure, footer, h1, h2, h3, h4, h5, h6, header, li, main, nav, "
            + "ol, p, pre, section, table, tbody, tfoot, thead, tr, ul";
    private final ObjectMapper objectMapper;

    @Override
    public SourceType type() {
        return SourceType.RSS;
    }

    @Override
    public List<FetchedItem> fetch(InfoSource source, Instant since) throws Exception {
        var config = objectMapper.readValue(source.getConfigJson() == null || source.getConfigJson().isBlank()
                ? "{}" : source.getConfigJson(), JsonNode.class);
        String feedUrl = config.path("url").asString("");
        if (feedUrl.isBlank() && source.getBaseUrl() != null) {
            feedUrl = source.getBaseUrl().trim();
        }
        if (feedUrl.isBlank()) {
            throw new IllegalArgumentException("RSS 源未配置 feed url(props url/baseUrl 均为空)");
        }
        String xml = WebClient.builder().build().get().uri(URI.create(feedUrl))
                .retrieve().bodyToMono(String.class).block();
        var feed = new SyndFeedInput().build(new StringReader(xml));
        List<FetchedItem> items = new ArrayList<>();
        LocalDateTime threshold = since == null ? null
                : LocalDateTime.ofInstant(since.minus(GRACE), ZoneId.systemDefault());
        for (SyndEntry entry : feed.getEntries()) {
            Date pub = entry.getPublishedDate() == null ? entry.getUpdatedDate() : entry.getPublishedDate();
            LocalDateTime published = pub == null ? LocalDateTime.now()
                    : LocalDateTime.ofInstant(pub.toInstant(), ZoneId.systemDefault());
            if (threshold != null && published.isBefore(threshold)) continue;
            String guid = entry.getUri() != null ? entry.getUri() : entry.getLink();
            String link = entry.getLink() != null ? entry.getLink() : feed.getLink() + "#" + guid;
            items.add(new FetchedItem(
                    guid,
                    entry.getTitle() == null ? "(无标题)" : entry.getTitle(),
                    link,
                    summaryOf(entry),
                    contentHtmlOf(entry),
                    entry.getAuthor() == null ? "" : entry.getAuthor(),
                    "",
                    "{}", 0, published));
        }
        return items;
    }

    private static String summaryOf(SyndEntry entry) {
        String description = entry.getDescription() == null ? null : entry.getDescription().getValue();
        String text = description != null && !description.isBlank()
                ? plainTextOf(description)
                : plainTextOf(contentHtmlOf(entry));
        return text.length() > SUMMARY_LIMIT ? text.substring(0, SUMMARY_LIMIT) : text;
    }

    private static String contentHtmlOf(SyndEntry entry) {
        StringBuilder raw = new StringBuilder();
        if (entry.getContents() != null) {
            for (SyndContent content : entry.getContents()) {
                if (content != null && content.getValue() != null) {
                    raw.append(content.getValue());
                }
            }
        }
        if (raw.isEmpty() && entry.getDescription() != null && entry.getDescription().getValue() != null) {
            raw.append(entry.getDescription().getValue());
        }
        if (raw.isEmpty()) return "";
        String baseUri = entry.getLink() == null ? "" : entry.getLink();
        return org.jsoup.Jsoup.clean(raw.toString(), baseUri, SAFELIST);
    }

    private static String plainTextOf(String html) {
        if (html == null || html.isBlank()) return "";
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);
        doc.outputSettings().prettyPrint(false);
        for (org.jsoup.nodes.Element element : doc.select(BLOCK_BREAK_TAGS)) {
            element.appendText("\n");
        }
        String text = doc.body().wholeText().replaceAll("\\n{3,}", "\n\n");
        return text.trim();
    }
}
