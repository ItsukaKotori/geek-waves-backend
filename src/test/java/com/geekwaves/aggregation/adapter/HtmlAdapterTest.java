package com.geekwaves.aggregation.adapter;

import com.geekwaves.aggregation.domain.InfoSource;
import com.geekwaves.config.WebClientConfig;
import com.geekwaves.config.domain.ProxyConfig;
import com.geekwaves.config.service.ProxySettingsService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HtmlAdapterTest {

    private static final String TEST_USER_AGENT = "geekwaves-test-agent/1.0";

    private MockWebServer server;
    private HtmlAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        adapter = new HtmlAdapter(new ObjectMapper(),
                WebClient.builder().defaultHeader("User-Agent", TEST_USER_AGENT));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private InfoSource sourceAtServer(String path) {
        InfoSource source = new InfoSource();
        source.setBaseUrl(server.url(path).toString());
        return source;
    }

    private static MockResponse html(String body) {
        return new MockResponse().setBody(body).addHeader("Content-Type", "text/html; charset=utf-8");
    }

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
    void nonBlankUnparsableTimeTextStillFallsBackToNow() throws Exception {
        String html = "<div class=\"item\"><a class=\"title\" href=\"http://t/1\">T</a>"
                + "<span class=\"time\">热点精选一周回顾</span></div>";

        List<FetchedItem> items = adapter.parseFromHtml(html, config(
                "{\"listSelector\":\".item\",\"titleSelector\":\".title\",\"timeSelector\":\".time\"}"));

        assertAround(items.get(0).publishedAt(), LocalDateTime.now());
    }

    // ---------- 一级:ISO-8601 原生支持 / timeAttr / timeFormat ----------

    @Test
    void isoLocalDateTimeTextParsesNatively() throws Exception {
        String html = "<div class=\"item\"><a class=\"title\" href=\"http://t/1\">T</a>"
                + "<span class=\"time\">2026-08-01T10:00:00</span></div>";

        List<FetchedItem> items = adapter.parseFromHtml(html, config(
                "{\"listSelector\":\".item\",\"titleSelector\":\".title\",\"timeSelector\":\".time\"}"));

        assertEquals(LocalDateTime.of(2026, 8, 1, 10, 0, 0), items.get(0).publishedAt());
    }

    @Test
    void isoOffsetDateTimeConvertedToSystemZone() throws Exception {
        String html = "<div class=\"item\"><a class=\"title\" href=\"http://t/1\">T</a>"
                + "<span class=\"time\">2026-08-01T10:00:00+08:00</span></div>";
        LocalDateTime expected = OffsetDateTime.parse("2026-08-01T10:00:00+08:00")
                .atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();

        List<FetchedItem> items = adapter.parseFromHtml(html, config(
                "{\"listSelector\":\".item\",\"titleSelector\":\".title\",\"timeSelector\":\".time\"}"));

        assertEquals(expected, items.get(0).publishedAt());
    }

    @Test
    void timeAttrReadsValueFromConfiguredAttribute() throws Exception {
        String html = "<div class=\"item\"><a class=\"title\" href=\"http://t/1\">T</a>"
                + "<time class=\"time\" datetime=\"2026-07-15T09:30:00\">昨天</time></div>";

        List<FetchedItem> items = adapter.parseFromHtml(html, config("""
                {"listSelector":".item","titleSelector":".title","timeSelector":".time",
                 "timeAttr":"datetime"}"""));

        assertEquals(LocalDateTime.of(2026, 7, 15, 9, 30), items.get(0).publishedAt());
    }

    @Test
    void customTimeFormatPatternParsesText() throws Exception {
        String html = "<div class=\"item\"><a class=\"title\" href=\"http://t/1\">T</a>"
                + "<span class=\"time\">2026/06/01 08:05</span></div>";

        List<FetchedItem> items = adapter.parseFromHtml(html, config("""
                {"listSelector":".item","titleSelector":".title","timeSelector":".time",
                 "timeFormat":"yyyy/MM/dd HH:mm"}"""));

        assertEquals(LocalDateTime.of(2026, 6, 1, 8, 5), items.get(0).publishedAt());
    }

    @Test
    void patternFailureFallsThroughToRelativeTimeThenNow() throws Exception {
        String html = """
                <div class="item"><a class="title" href="http://t/1">First</a><span class="time">5 分钟前</span></div>
                <div class="item"><a class="title" href="http://t/2">Second</a><span class="time">???</span></div>""";

        List<FetchedItem> items = adapter.parseFromHtml(html, config("""
                {"listSelector":".item","titleSelector":".title","timeSelector":".time",
                 "timeFormat":"yyyy-MM-dd HH:mm:ss"}"""));

        assertAround(items.get(0).publishedAt(), LocalDateTime.now().minusMinutes(5));
        assertAround(items.get(1).publishedAt(), LocalDateTime.now());
    }

    // ---------- 二级:中英文相对时间 ----------

    @Test
    void chineseRelativeMinutesAgo() throws Exception {
        for (String text : new String[]{"3 分钟前", "3分钟前"}) {
            String html = htmlWithTime(text);

            List<FetchedItem> items = adapter.parseFromHtml(html, baseTimeConfig());

            assertAround(items.get(0).publishedAt(), LocalDateTime.now().minusMinutes(3));
        }
    }

    @Test
    void chineseSecondsAndHoursAndDaysAgo() throws Exception {
        String[] texts = {"45 秒前", "2 小时前", "5 天前"};
        LocalDateTime[] expecteds = {LocalDateTime.now().minusSeconds(45),
                LocalDateTime.now().minusHours(2), LocalDateTime.now().minusDays(5)};
        for (int i = 0; i < texts.length; i++) {
            List<FetchedItem> items = adapter.parseFromHtml(htmlWithTime(texts[i]), baseTimeConfig());
            assertAround(items.get(0).publishedAt(), expecteds[i]);
        }
    }

    @Test
    void chineseWeeksMonthsYearsAgo() throws Exception {
        String[] texts = {"3 周前", "2 个月前", "1 年前"};
        LocalDateTime[] expecteds = {LocalDateTime.now().minusDays(21),
                LocalDateTime.now().minusDays(60), LocalDateTime.now().minusDays(365)};
        for (int i = 0; i < texts.length; i++) {
            List<FetchedItem> items = adapter.parseFromHtml(htmlWithTime(texts[i]), baseTimeConfig());
            assertAround(items.get(0).publishedAt(), expecteds[i]);
        }
    }

    @Test
    void justNowFormsResolveToNow() throws Exception {
        for (String text : new String[]{"刚刚", "刚才", "just now", "Just Now"}) {
            List<FetchedItem> items = adapter.parseFromHtml(htmlWithTime(text), baseTimeConfig());
            assertAround(items.get(0).publishedAt(), LocalDateTime.now());
        }
    }

    @Test
    void englishRelativeTimesAgo() throws Exception {
        String[] texts = {"10 minutes ago", "2 hours ago", "3 days ago", "7 secs ago", "2 weeks ago", "6 months ago"};
        LocalDateTime[] expecteds = {LocalDateTime.now().minusMinutes(10), LocalDateTime.now().minusHours(2),
                LocalDateTime.now().minusDays(3), LocalDateTime.now().minusSeconds(7),
                LocalDateTime.now().minusDays(14), LocalDateTime.now().minusDays(180)};
        for (int i = 0; i < texts.length; i++) {
            List<FetchedItem> items = adapter.parseFromHtml(htmlWithTime(texts[i]), baseTimeConfig());
            assertAround(items.get(0).publishedAt(), expecteds[i]);
        }
    }

    @Test
    void compactShorthandUnitsParseWithoutMarker() throws Exception {
        String[] texts = {"12m", "6h", "3d"};
        LocalDateTime[] expecteds = {LocalDateTime.now().minusMinutes(12),
                LocalDateTime.now().minusHours(6), LocalDateTime.now().minusDays(3)};
        for (int i = 0; i < texts.length; i++) {
            List<FetchedItem> items = adapter.parseFromHtml(htmlWithTime(texts[i]), baseTimeConfig());
            assertAround(items.get(0).publishedAt(), expecteds[i]);
        }
    }

    @Test
    void hugeOverflowNumberFallsBackToNowInsteadOfCrashing() throws Exception {
        List<FetchedItem> items = adapter.parseFromHtml(
                htmlWithTime("99999999999999999 years ago"), baseTimeConfig());

        assertAround(items.get(0).publishedAt(), LocalDateTime.now());
    }

    @Test
    void unknownUnitYieldsNullInsteadOfEscapingTheContainer() {
        assertNull(HtmlAdapter.beforeNow(5, "刻钟"));
        assertNull(HtmlAdapter.beforeNow(1, "fortnight"));
    }

    private static String htmlWithTime(String timeText) {
        return "<div class=\"item\"><a class=\"title\" href=\"http://t/1\">T</a>"
                + "<span class=\"time\">" + timeText + "</span></div>";
    }

    private static tools.jackson.databind.JsonNode baseTimeConfig() throws Exception {
        return config("{\"listSelector\":\".item\",\"titleSelector\":\".title\",\"timeSelector\":\".time\"}");
    }

    private static void assertAround(LocalDateTime actual, LocalDateTime expected) {
        assertFalse(actual.isBefore(expected.minusSeconds(65)), actual + " not around " + expected);
        assertFalse(actual.isAfter(expected.plusSeconds(65)), actual + " not around " + expected);
    }

    @Test
    void sourceItemIdUsesSha256AlignedWithNewsServiceRule() throws Exception {
        String html = "<div class=\"item\"><a class=\"title\" href=\"http://t/1\">Hello</a></div>";

        List<FetchedItem> first = adapter.parseFromHtml(html, config(
                "{\"listSelector\":\".item\",\"titleSelector\":\".title\"}"));
        List<FetchedItem> second = adapter.parseFromHtml(html, config(
                "{\"listSelector\":\".item\",\"titleSelector\":\".title\"}"));

        String raw = "Hello" + "http://t/1";
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
        String sha40 = HexFormat.of().formatHex(digest).substring(0, 40);

        assertEquals(sha40, first.get(0).sourceItemId());
        assertEquals(40, first.get(0).sourceItemId().length());
        assertEquals(first.get(0).sourceItemId(), second.get(0).sourceItemId());
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

    @Test
    void fetchRequestsViaInjectedWebClientAndParsesResponse() throws Exception {
        server.enqueue(html("""
                <div class="item"><a class="title" href="/u/1">First</a><span class="time">5m</span></div>
                <div class="item"><a class="title" href="/u/2">Second</a></div>"""));
        InfoSource source = sourceAtServer("/list");
        source.setConfigJson("{\"listSelector\":\".item\",\"titleSelector\":\".title\",\"hrefAttr\":\"href\",\"timeSelector\":\".time\"}");

        List<FetchedItem> items = adapter.fetch(source, null);

        assertEquals(2, items.size());
        assertEquals("First", items.get(0).title());
        assertEquals(server.url("/u/1").toString(), items.get(0).url());

        RecordedRequest request = server.takeRequest();
        assertEquals("/list", request.getPath());
        assertEquals(TEST_USER_AGENT, request.getHeader("User-Agent"));
    }

    @Test
    void fetchResolvesRelativeUrlsAgainstSourceBaseUrl() throws Exception {
        server.enqueue(html("<div class=\"item\"><a class=\"title\" href=\"/u/1\">T</a></div>"));
        InfoSource source = sourceAtServer("/page.html");
        source.setConfigJson("{\"listSelector\":\".item\",\"titleSelector\":\".title\",\"hrefAttr\":\"href\"}");

        List<FetchedItem> items = adapter.fetch(source, null);

        assertEquals(server.url("/u/1").toString(), items.get(0).url());
    }

    @Test
    void fetchFollowsHttpRedirectAndParsesTargetContent() throws Exception {
        ProxyConfig proxyDisabled = new ProxyConfig();
        proxyDisabled.setEnabled(false);
        proxyDisabled.setHost("127.0.0.1");
        proxyDisabled.setPort(7890);
        ProxySettingsService svc = mock(ProxySettingsService.class);
        when(svc.effective()).thenReturn(proxyDisabled);
        when(svc.rawPassword()).thenReturn(null);
        HtmlAdapter configured = new HtmlAdapter(new ObjectMapper(),
                new WebClientConfig(svc).webClientBuilder());

        server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", "/redirected"));
        server.enqueue(html("<div class=\"item\"><a class=\"title\" href=\"/u/9\">Redirected</a></div>"));
        InfoSource source = sourceAtServer("/list");
        source.setConfigJson("{\"listSelector\":\".item\",\"titleSelector\":\".title\",\"hrefAttr\":\"href\"}");

        List<FetchedItem> items = configured.fetch(source, null);

        assertEquals(1, items.size());
        assertEquals("Redirected", items.get(0).title());
        assertEquals(2, server.getRequestCount());
    }
}
