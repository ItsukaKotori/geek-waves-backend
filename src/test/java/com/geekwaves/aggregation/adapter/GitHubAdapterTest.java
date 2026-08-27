package com.geekwaves.aggregation.adapter;

import com.geekwaves.aggregation.domain.InfoSource;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GitHubAdapterTest {

    private static final String TEST_USER_AGENT = "geekwaves-test-agent/1.0";

    private MockWebServer server;
    private GitHubAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        adapter = new GitHubAdapter(WebClient.builder().defaultHeader("User-Agent", TEST_USER_AGENT),
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private InfoSource sourceAtServer() {
        InfoSource source = new InfoSource();
        source.setBaseUrl(server.url("/").toString());
        return source;
    }

    private static MockResponse json(String body) {
        return new MockResponse().setBody(body).addHeader("Content-Type", "application/json");
    }

    private static MockResponse html(String body) {
        return new MockResponse().setBody(body).addHeader("Content-Type", "text/html; charset=utf-8");
    }

    @Test
    void typeIsGithubApi() {
        assertEquals(SourceType.GITHUB_API, adapter.type());
    }

    @Test
    void searchMapsFieldsAndSendsQueryWithoutToken() throws Exception {
        server.enqueue(json("""
                {"total_count":2,"items":[
                  {"full_name":"foo/bar","description":"a great lib","html_url":"https://github.com/foo/bar",
                   "owner":{"login":"alice"},"language":"Java","stargazers_count":1234,"forks_count":56,
                   "created_at":"2026-02-01T00:00:00Z"},
                  {"full_name":"old/repo","description":"old lib","html_url":"https://github.com/old/repo",
                   "owner":{"login":"bob"},"language":"Go","stargazers_count":100,"forks_count":5,
                   "created_at":"2025-01-01T00:00:00Z"}
                ]}"""));
        InfoSource source = sourceAtServer();
        source.setConfigJson("{\"minStars\":100,\"tag\":\"java\"}");

        List<FetchedItem> items = adapter.fetch(source, Instant.parse("2026-01-15T00:00:00Z"));

        assertEquals(1, items.size());
        FetchedItem item = items.get(0);
        assertEquals("foo/bar", item.sourceItemId());
        assertEquals("https://github.com/foo/bar", item.url());
        assertEquals("alice", item.author());
        assertEquals("Java", item.tagsString());
        assertEquals(1234, item.score());
        assertTrue(item.extraJson().contains("\"stars\":1234"));
        assertTrue(item.extraJson().contains("\"forks\":56"));

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getPath().startsWith("/search/repositories?q=created"));
        assertTrue(request.getPath().contains("created:%3E2026-01-13"));
        assertTrue(request.getPath().contains("stars:%3E%3D100"));
        assertTrue(request.getPath().contains("sort=stars"));
        assertNull(request.getHeader("Authorization"));
    }

    @Test
    void tokenAddsBearerHeader() throws Exception {
        server.enqueue(json("{\"total_count\":0,\"items\":[]}"));
        InfoSource source = sourceAtServer();
        source.setConfigJson("{\"token\":\"secret\"}");

        adapter.fetch(source, null);

        assertEquals("Bearer secret", server.takeRequest().getHeader("Authorization"));
    }

    @Test
    void invalidCreatedAtSkipsEntryWithoutBreakingFetch() throws Exception {
        server.enqueue(json("""
                {"total_count":2,"items":[
                  {"full_name":"bad/repo","description":"x","html_url":"https://github.com/bad/repo",
                   "owner":{"login":"c"},"language":"Rust","stargazers_count":7,"forks_count":0,
                   "created_at":"not-a-date"},
                  {"full_name":"good/repo","description":"y","html_url":"https://github.com/good/repo",
                   "owner":{"login":"d"},"language":"Rust","stargazers_count":8,"forks_count":1,
                   "created_at":"2026-03-01T00:00:00Z"}
                ]}"""));
        InfoSource source = sourceAtServer();
        source.setConfigJson("{\"minStars\":100}");

        List<FetchedItem> items = adapter.fetch(source, null);

        assertEquals(1, items.size());
        assertEquals("good/repo", items.get(0).sourceItemId());
    }

    @Test
    void parseTrendingHtml() {
        String html = "<article class=\"Box-row\"><h2><a>foo / bar</a></h2>"
                + "<p>a great lib</p><span class=\"d-inline-block\">1,234</span></article>";
        List<FetchedItem> items = adapter.fetchTrendingFromHtml(html);
        assertEquals(1, items.size());
        assertEquals("foo/bar", items.get(0).sourceItemId());
        assertEquals(1234, items.get(0).score());
    }

    @Test
    void trendingHtmlFetchedThroughInjectedWebClient() throws Exception {
        server.enqueue(html("""
                <article class="Box-row"><h2><a>foo / bar</a></h2>
                <p>a great lib</p><span class="d-inline-block">1,234</span></article>"""));
        InfoSource source = sourceAtServer();
        source.setConfigJson("{\"html\":\"" + server.url("/trending") + "\"}");

        List<FetchedItem> items = adapter.fetch(source, null);

        assertEquals(1, items.size());
        assertEquals("foo/bar", items.get(0).sourceItemId());
        assertEquals(1234, items.get(0).score());

        RecordedRequest request = server.takeRequest();
        assertEquals("/trending", request.getPath());
        assertEquals(TEST_USER_AGENT, request.getHeader("User-Agent"));
    }
}
