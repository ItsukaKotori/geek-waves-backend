package com.geekwaves.aggregation.framework;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.geekwaves.aggregation.domain.FrameworkWatch;
import com.geekwaves.aggregation.domain.NewsItem;
import com.geekwaves.aggregation.domain.mapper.FrameworkWatchMapper;
import com.geekwaves.aggregation.domain.mapper.NewsItemMapper;
import com.geekwaves.config.port.CachePort;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.itsuka.core.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FrameworkFetchServiceTest {

    private MockWebServer server;
    private FrameworkWatchMapper watchMapper;
    private NewsItemMapper newsItemMapper;
    private CachePort cachePort;
    private FrameworkFetchService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        watchMapper = mock(FrameworkWatchMapper.class);
        newsItemMapper = mock(NewsItemMapper.class);
        cachePort = mock(CachePort.class);
        service = new FrameworkFetchService(watchMapper, WebClient.builder(), new ObjectMapper(), newsItemMapper, cachePort);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private static MockResponse json(String body) {
        return new MockResponse().setBody(body).addHeader("Content-Type", "application/json");
    }

    private static FrameworkWatch watch(String repo) {
        FrameworkWatch w = new FrameworkWatch();
        w.setId(1L);
        w.setName("Spring Boot");
        w.setGithubRepo(repo);
        return w;
    }

    @Test
    void fetchNowUpdatesVersionAndReleaseTime() throws Exception {
        server.enqueue(json("""
                {"tag_name":"v3.4.1","published_at":"2026-08-20T10:00:00Z"}
                """));
        FrameworkWatch w = watch("spring-projects/spring-boot");

        boolean ok = service.fetchNow(w, server.url("/").toString());

        assertTrue(ok);
        assertEquals("v3.4.1", w.getLatestVersion());
        assertNotNull(w.getLastReleaseAt());
        RecordedRequest req = server.takeRequest();
        assertEquals("/repos/spring-projects/spring-boot/releases/latest", req.getPath());
        verify(watchMapper).updateById(w);
    }

    @Test
    void fetchNowReturnsFalseOn404WithoutThrowing() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("Not Found"));
        FrameworkWatch w = watch("foo/bar");

        assertFalse(service.fetchNow(w, server.url("/").toString()));
        verify(watchMapper, never()).updateById(any(FrameworkWatch.class));
    }

    @Test
    void fetchNowRejectsMalformedRepoWithoutRequest() {
        FrameworkWatch w = watch("foo");
        assertFalse(service.fetchNow(w, server.url("/").toString()));
        assertEquals(0, server.getRequestCount());

        w.setGithubRepo("a/b/c");
        assertFalse(service.fetchNow(w, server.url("/").toString()));
        assertEquals(0, server.getRequestCount());

        w.setGithubRepo(null);
        assertFalse(service.fetchNow(w, server.url("/").toString()));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void normalizeRepoAcceptsUrlsAndGitSuffix() {
        assertEquals("spring-projects/spring-boot",
                FrameworkFetchService.normalizeRepo("https://github.com/spring-projects/spring-boot.git"));
        assertEquals("spring-projects/spring-boot",
                FrameworkFetchService.normalizeRepo("https://github.com/spring-projects/spring-boot/"));
        assertEquals("spring-projects/spring-boot",
                FrameworkFetchService.normalizeRepo("github.com/spring-projects/spring-boot"));
        assertEquals("spring-projects/spring-boot",
                FrameworkFetchService.normalizeRepo("  spring-projects/spring-boot  "));
        assertNull(FrameworkFetchService.normalizeRepo("https://example.com/a/b"));
        assertNull(FrameworkFetchService.normalizeRepo("a/b/c"));
        assertNull(FrameworkFetchService.normalizeRepo(null));
    }

    @Test
    void fetchNowAcceptsFullUrlRepo() throws Exception {
        server.enqueue(json("{\"tag_name\":\"v4.0.0\"}"));
        FrameworkWatch w = watch("https://github.com/spring-projects/spring-boot.git");
        assertTrue(service.fetchNow(w, server.url("/").toString()));
        assertEquals("v4.0.0", w.getLatestVersion());
    }

    @Test
    void fetchNowReturnsFalseWhenNoTagName() throws Exception {
        server.enqueue(json("{\"published_at\":\"2026-08-20T10:00:00Z\"}"));
        FrameworkWatch w = watch("foo/bar");

        assertFalse(service.fetchNow(w, server.url("/").toString()));
        verify(watchMapper, never()).updateById(any(FrameworkWatch.class));
    }

    @Test
    void fetchNowTrimsTagWithoutStrippingVPrefix() throws Exception {
        server.enqueue(json("{\"tag_name\":\" v3.4.1 \"}"));
        FrameworkWatch w = watch("foo/bar");

        assertTrue(service.fetchNow(w, server.url("/").toString()));
        assertEquals("v3.4.1", w.getLatestVersion());
    }

    @Test
    void refreshThrows404WhenWatchMissing() {
        when(watchMapper.selectById(99L)).thenReturn(null);

        ServiceException e = assertThrows(ServiceException.class, () -> service.refresh(99L));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void refreshFetchesAndReturnsUpdatedWatch() throws Exception {
        server.enqueue(json("""
                {"tag_name":"v3.4.1","published_at":"2026-08-20T10:00:00Z"}
                """));
        FrameworkWatch w = watch("spring-projects/spring-boot");
        when(watchMapper.selectById(1L)).thenReturn(w);

        FrameworkWatch result = service.refresh(1L, server.url("/").toString());

        assertEquals("v3.4.1", result.getLatestVersion());
        verify(watchMapper).updateById(w);
    }

    @Test
    void fetchNowPublishesReleaseNewsAndInvalidatesCache() throws Exception {
        String notes = "## What's Changed\n- Bump spring-batch from 5.1.0 to 5.1.1\n- fix: 若干兼容性修复\n\n**Full Changelog**: https://github.com/spring-projects/spring-boot/compare/v3.4.0...v3.4.1";
        server.enqueue(json("""
                {"tag_name":"v3.4.1","published_at":"2026-08-20T10:00:00Z","body":"%s"}
                """.formatted(notes.replace("\n", "\\n"))));
        FrameworkWatch w = watch("spring-projects/spring-boot");

        assertTrue(service.fetchNow(w, server.url("/").toString()));

        ArgumentCaptor<NewsItem> captor = ArgumentCaptor.forClass(NewsItem.class);
        verify(newsItemMapper).insert(captor.capture());
        NewsItem item = captor.getValue();
        assertEquals("RELEASE", item.getCategory());
        assertEquals(1L, item.getSourceId());
        assertEquals("v3.4.1", item.getSourceItemId());
        assertTrue(item.getTitle().contains("v3.4.1"));
        assertEquals("https://github.com/spring-projects/spring-boot/releases/tag/v3.4.1", item.getUrl());
        assertEquals("spring-projects/spring-boot", item.getTags());
        assertEquals("NONE", item.getAiStatus());
        assertNotNull(item.getPublishedAt());
        assertNotNull(item.getFetchedAt());
        verify(cachePort).invalidateByPrefix("news:list:");
        assertTrue(item.getContent().contains("Bump spring-batch"));
        assertEquals(item.getContent(), item.getSummary());
    }

    @Test
    void fetchNowTruncatesLongNotesInSummaryButKeepsFullContent() throws Exception {
        String notes = "#".repeat(1000);
        server.enqueue(json("""
                {"tag_name":"v3.4.1","published_at":"2026-08-20T10:00:00Z","body":"%s"}
                """.formatted(notes)));
        FrameworkWatch w = watch("spring-projects/spring-boot");

        assertTrue(service.fetchNow(w, server.url("/").toString()));

        ArgumentCaptor<NewsItem> captor = ArgumentCaptor.forClass(NewsItem.class);
        verify(newsItemMapper).insert(captor.capture());
        NewsItem item = captor.getValue();
        assertTrue(item.getSummary().length() <= 300);
        assertEquals(1000, item.getContent().length());
    }

    @Test
    void fetchNowPublishesNewsWhenPublishedAtMissing() throws Exception {
        server.enqueue(json("{\"tag_name\":\"v4.0.0\"}"));
        FrameworkWatch w = watch("spring-projects/spring-boot");

        assertTrue(service.fetchNow(w, server.url("/").toString()));

        ArgumentCaptor<NewsItem> captor = ArgumentCaptor.forClass(NewsItem.class);
        verify(newsItemMapper).insert(captor.capture());
        assertNotNull(captor.getValue().getPublishedAt());
    }

    @Test
    void fetchNowUpdatesExistingNewsInsteadOfDuplicateInsert() throws Exception {
        server.enqueue(json("""
                {"tag_name":"v3.4.1","published_at":"2026-08-20T10:00:00Z","body":"## What's Changed\\n- fix: security patch"}
                """));
        FrameworkWatch w = watch("spring-projects/spring-boot");
        NewsItem existing = new NewsItem();
        existing.setId(9L);
        existing.setSourceId(1L);
        existing.setSourceItemId("v3.4.1");
        when(newsItemMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        assertTrue(service.fetchNow(w, server.url("/").toString()));

        verify(newsItemMapper, never()).insert(any(NewsItem.class));
        ArgumentCaptor<NewsItem> captor = ArgumentCaptor.forClass(NewsItem.class);
        verify(newsItemMapper).updateById(captor.capture());
        NewsItem updated = captor.getValue();
        assertEquals(9L, updated.getId());
        assertTrue(updated.getContent().contains("fix: security patch"));
        assertEquals("spring-projects/spring-boot", updated.getTags());
    }

    @Test
    void fetchNowStillSucceedsWhenInsertFails() throws Exception {
        server.enqueue(json("""
                {"tag_name":"v3.4.1","published_at":"2026-08-20T10:00:00Z"}
                """));
        FrameworkWatch w = watch("spring-projects/spring-boot");
        when(newsItemMapper.insert(any(NewsItem.class))).thenThrow(new DuplicateKeyException("dup"));

        assertTrue(service.fetchNow(w, server.url("/").toString()));

        assertEquals("v3.4.1", w.getLatestVersion());
        verify(cachePort).invalidateByPrefix("news:list:");
    }

    @Test
    void fetchNowStillSucceedsWhenInsertThrowsGenericException() throws Exception {
        server.enqueue(json("""
                {"tag_name":"v3.4.1","published_at":"2026-08-20T10:00:00Z"}
                """));
        FrameworkWatch w = watch("spring-projects/spring-boot");
        when(newsItemMapper.insert(any(NewsItem.class))).thenThrow(new IllegalStateException("db down"));

        assertTrue(service.fetchNow(w, server.url("/").toString()));
        assertEquals("v3.4.1", w.getLatestVersion());
    }
}
