package com.geekwaves.aggregation.adapter;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HtmlAdapterTest {

    private final HtmlAdapter adapter = new HtmlAdapter(new ObjectMapper());

    private static tools.jackson.databind.JsonNode config(String json) throws Exception {
        return new ObjectMapper().readTree(json);
    }

    @Test
    void typeIsHtml() {
        assertEquals(SourceType.HTML, adapter.type());
    }

    @Test
    void parseFromHtmlMapsSelectors() throws Exception {
        String html = """
                <div class="list">
                  <div class="item"><a class="title" href="http://t/1">First</a><span class="time">5m</span></div>
                  <div class="item"><a class="title" href="http://t/2">Second</a></div>
                </div>""";

        List<FetchedItem> items = adapter.parseFromHtml(html, config("""
                {"listSelector":".item","titleSelector":".title","hrefAttr":"href","timeSelector":".time"}"""));

        assertEquals(2, items.size());
        FetchedItem first = items.get(0);
        assertEquals("First", first.title());
        assertEquals("http://t/1", first.url());
        assertEquals("First5m", first.content());
        assertTrue(first.summary().contains("5m"));
        assertEquals("Second", items.get(1).title());
    }

    @Test
    void skipsRowsWithoutTitle() throws Exception {
        String html = """
                <div class="item"><a class="title" href="http://t/1">Real</a></div>
                <div class="item"><a class="title" href="http://t/2"></a></div>
                <div class="item"><p>No title el</p></div>""";

        List<FetchedItem> items = adapter.parseFromHtml(html, config(
                "{\"listSelector\":\".item\",\"titleSelector\":\".title\"}"));

        assertEquals(1, items.size());
        assertEquals("Real", items.get(0).title());
    }

    @Test
    void emptyTitleUrlFallsBackToUrlSelector() throws Exception {
        String html = """
                <div class="item"><a class="title">NoHref</a><a class="link" href="http://fallback/1">l</a></div>""";

        List<FetchedItem> items = adapter.parseFromHtml(html, config("""
                {"listSelector":".item","titleSelector":".title","urlSelector":".link","hrefAttr":"href"}"""));

        assertEquals(1, items.size());
        assertEquals("http://fallback/1", items.get(0).url());
    }

    @Test
    void blankTimeTextYieldsNow() throws Exception {
        String html = "<div class=\"item\"><a class=\"title\" href=\"http://t/1\">T</a></div>";

        List<FetchedItem> items = adapter.parseFromHtml(html, config(
                "{\"listSelector\":\".item\",\"titleSelector\":\".title\",\"timeSelector\":\".time\"}"));

        LocalDateTime now = LocalDateTime.now();
        assertTrue(items.get(0).publishedAt().isAfter(now.minusSeconds(60)));
        assertTrue(items.get(0).publishedAt().isBefore(now.plusSeconds(60)));
    }

    @Test
    void nonBlankTimeTextYieldsNowMinusHour() throws Exception {
        String html = "<div class=\"item\"><a class=\"title\" href=\"http://t/1\">T</a>"
                + "<span class=\"time\">5m</span></div>";

        List<FetchedItem> items = adapter.parseFromHtml(html, config(
                "{\"listSelector\":\".item\",\"titleSelector\":\".title\",\"timeSelector\":\".time\"}"));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expected = now.minusHours(1);
        assertTrue(items.get(0).publishedAt().isAfter(expected.minusSeconds(60)));
        assertTrue(items.get(0).publishedAt().isBefore(expected.plusSeconds(60)));
    }

    @Test
    void relativeUrlWithoutBaseStaysEmpty() throws Exception {
        String html = "<div class=\"item\"><a class=\"title\" href=\"/u/1\">T</a></div>";

        List<FetchedItem> items = adapter.parseFromHtml(html, config(
                "{\"listSelector\":\".item\",\"titleSelector\":\".title\"}"));

        assertEquals("", items.get(0).url());
    }

    @Test
    void relativeUrlResolvedAgainstFetchDocumentBase() throws Exception {
        String html = "<div class=\"item\"><a class=\"title\" href=\"/u/1\">T</a></div>";

        List<FetchedItem> items = adapter.parseFromDoc(
                org.jsoup.Jsoup.parse(html, "https://example.com/base"), config(
                        "{\"listSelector\":\".item\",\"titleSelector\":\".title\"}"));

        assertEquals("https://example.com/u/1", items.get(0).url());
    }
}
