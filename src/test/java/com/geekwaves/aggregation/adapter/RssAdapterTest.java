package com.geekwaves.aggregation.adapter;

import com.geekwaves.aggregation.domain.InfoSource;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RssAdapterTest {

    private MockWebServer server;
    private RssAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        adapter = new RssAdapter(new ObjectMapper(), WebClient.builder());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private InfoSource sourceAtServer() {
        InfoSource source = new InfoSource();
        source.setBaseUrl(server.url("/feed.xml").toString());
        return source;
    }

    private static MockResponse xml(String body) {
        return new MockResponse().setBody(body).addHeader("Content-Type", "application/rss+xml");
    }

    private static String feed(String items) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<rss version=\"2.0\" xmlns:content=\"http://purl.org/rss/1.0/modules/content/\"><channel><title>Feed</title><link>http://feed.example/</link>"
                + items
                + "</channel></rss>";
    }

    @Test
    void typeIsRss() {
        assertEquals(SourceType.RSS, adapter.type());
    }

    @Test
    void fetchParsesFeedAndMapsFields() throws Exception {
        server.enqueue(xml(feed("""
                <item><guid>1</guid><title>Hello RSS</title><link>http://t/1</link>
                  <pubDate>Wed, 11 Jun 2025 11:55:00 GMT</pubDate>
                  <description>desc1</description><author>u1@example.com</author></item>
                <item><guid>2</guid></item>""")));

        List<FetchedItem> items = adapter.fetch(sourceAtServer(), null);

        assertEquals(2, items.size());
        FetchedItem first = items.get(0);
        assertEquals("1", first.sourceItemId());
        assertEquals("Hello RSS", first.title());
        assertEquals("http://t/1", first.url());
        assertEquals("desc1", first.summary());
        assertEquals("u1@example.com", first.author());
        assertEquals(LocalDateTime.ofInstant(Instant.parse("2025-06-11T11:55:00Z"), ZoneId.systemDefault()), first.publishedAt());
        assertEquals("(无标题)", items.get(1).title());
        assertEquals("2", items.get(1).url());
        assertEquals("", items.get(1).summary());
    }

    @Test
    void fetchStripsHtmlFromSummary() throws Exception {
        server.enqueue(xml(feed("""
                <item><guid>1</guid><title>T</title><link>http://t/1</link>
                  <pubDate>Wed, 11 Jun 2025 11:55:00 GMT</pubDate>
                  <content:encoded><![CDATA[<p>Hello <b>world</b></p><p><a href="http://x">link</a></p>]]></content:encoded>
                </item>""")));

        List<FetchedItem> items = adapter.fetch(sourceAtServer(), null);

        assertEquals(1, items.size());
        assertEquals("Hello world\n\nlink", items.get(0).summary());
    }

    @Test
    void contentStoredFromContentEncoded() throws Exception {
        server.enqueue(xml(feed("""
                <item><guid>1</guid><title>T</title><link>http://t/1</link>
                  <description>Short teaser</description>
                  <content:encoded><![CDATA[<p>First para</p><p>Second <a href="http://x/y">link</a></p>]]></content:encoded>
                </item>""")));

        List<FetchedItem> items = adapter.fetch(sourceAtServer(), null);

        assertEquals(1, items.size());
        String content = items.get(0).content();
        assertFalse(content.isBlank());
        assertTrue(content.contains("<p>"));
        assertTrue(content.contains("First para"));
        assertEquals("Short teaser", items.get(0).summary());
    }

    @Test
    void contentSanitizedAgainstXss() throws Exception {
        server.enqueue(xml(feed("""
                <item><guid>1</guid><title>T</title><link>http://t/1</link>
                  <description>teaser</description>
                  <content:encoded><![CDATA[<p>para</p><script>alert('xss')</script>
                    <img src="http://t/img.png" onerror="steal()">
                    <iframe src="http://evil.example"></iframe>
                    <a href="http://t/doc">ok</a>]]></content:encoded>
                </item>""")));

        List<FetchedItem> items = adapter.fetch(sourceAtServer(), null);

        String content = items.get(0).content();
        assertFalse(content.contains("script"));
        assertFalse(content.contains("onerror"));
        assertFalse(content.contains("iframe"));
        assertTrue(content.contains("<p>"));
        assertTrue(content.contains("<a "));
        assertTrue(content.contains("<img "));
    }

    @Test
    void contentFallsBackToDescriptionWhenEncodedMissing() throws Exception {
        server.enqueue(xml(feed("""
                <item><guid>1</guid><title>T</title><link>http://t/1</link>
                  <description><![CDATA[<p>Desc <b>body</b></p>]]></description>
                </item>""")));

        List<FetchedItem> items = adapter.fetch(sourceAtServer(), null);

        assertTrue(items.get(0).content().contains("<p>Desc <b>body</b></p>"));
        assertEquals("Desc body", items.get(0).summary());
    }

    @Test
    void blankDescriptionFallsBackToContentSummary() throws Exception {
        server.enqueue(xml(feed("""
                <item><guid>1</guid><title>T</title><link>http://t/1</link>
                  <description></description>
                  <content:encoded><![CDATA[<p>Full body paragraph one</p><p>paragraph two</p>]]></content:encoded>
                </item>""")));

        List<FetchedItem> items = adapter.fetch(sourceAtServer(), null);

        assertEquals("Full body paragraph one\n\nparagraph two", items.get(0).summary());
    }

    @Test
    void summaryKeepsParagraphBreaks() throws Exception {
        server.enqueue(xml(feed("""
                <item><guid>1</guid><title>T</title><link>http://t/1</link>
                  <description><![CDATA[<p>One</p><p>Two</p>]]></description>
                </item>""")));

        List<FetchedItem> items = adapter.fetch(sourceAtServer(), null);

        assertTrue(items.get(0).summary().contains("\n"));
    }

    @Test
    void configUrlOverridesBaseUrl() throws Exception {
        server.enqueue(xml(feed("<item><guid>1</guid><title>Alt</title><link>http://alt/1</link></item>")));
        InfoSource source = new InfoSource();
        source.setBaseUrl("http://not-used.example/feed.xml");
        source.setConfigJson("{\"url\":\"" + server.url("/alt.xml").toString() + "\"}");

        List<FetchedItem> items = adapter.fetch(source, null);

        assertEquals(1, items.size());
        assertEquals("Alt", items.get(0).title());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void blankConfigUrlFallsBackToBaseUrl() throws Exception {
        server.enqueue(xml(feed("<item><guid>1</guid><title>F</title><link>http://f/1</link></item>")));
        InfoSource source = new InfoSource();
        source.setBaseUrl(server.url("/feed.xml").toString());
        source.setConfigJson("{\"url\":\"\"}");

        List<FetchedItem> items = adapter.fetch(source, null);

        assertEquals(1, items.size());
        assertEquals("F", items.get(0).title());
        assertEquals("/feed.xml", server.getRequestCount() > 0 ? server.takeRequest().getPath() : "");
    }

    @Test
    void summaryTruncatedToLimit() throws Exception {
        String longDesc = "x".repeat(5000);
        server.enqueue(xml(feed("""
                <item><guid>1</guid><title>T</title><link>http://t/1</link>
                  <description>%s</description></item>
                """.formatted(longDesc))));
        List<FetchedItem> items = adapter.fetch(sourceAtServer(), null);
        assertEquals(1, items.size());
        assertEquals(2000, items.get(0).summary().length());
    }

    @Test
    void missingBothUrlsThrowsClearError() {
        InfoSource source = new InfoSource();
        source.setBaseUrl("");
        source.setConfigJson("{}");
        assertThrows(IllegalArgumentException.class, () -> adapter.fetch(source, null));
    }

    @Test
    void sinceSkipsItemsOlderThanTenMinuteGrace() throws Exception {
        server.enqueue(xml(feed("""
                <item><guid>1</guid><title>late</title><pubDate>Wed, 11 Jun 2025 11:55:00 GMT</pubDate></item>
                <item><guid>2</guid><title>old</title><pubDate>Wed, 11 Jun 2025 11:49:00 GMT</pubDate></item>
                <item><guid>3</guid><title>edge</title><pubDate>Wed, 11 Jun 2025 11:50:00 GMT</pubDate></item>""")));

        List<FetchedItem> items = adapter.fetch(sourceAtServer(), Instant.parse("2025-06-11T12:00:00Z"));

        assertEquals(2, items.size());
        assertEquals("late", items.get(0).title());
        assertEquals("edge", items.get(1).title());
    }

    @Test
    void nullSinceKeepsAllItems() throws Exception {
        server.enqueue(xml(feed("""
                <item><guid>1</guid><title>old</title><pubDate>Mon, 01 Jan 2018 00:00:00 GMT</pubDate></item>
                <item><guid>2</guid><title>older</title><pubDate>Sun, 01 Jan 2017 00:00:00 GMT</pubDate></item>""")));

        List<FetchedItem> items = adapter.fetch(sourceAtServer(), null);

        assertEquals(2, items.size());
    }
}
