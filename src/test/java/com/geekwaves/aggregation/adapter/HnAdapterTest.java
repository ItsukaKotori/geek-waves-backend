package com.geekwaves.aggregation.adapter;

import com.geekwaves.aggregation.domain.InfoSource;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HnAdapterTest {

    private MockWebServer server;
    private HnAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        adapter = new HnAdapter(WebClient.builder());
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

    @Test
    void typeIsHnApi() {
        assertEquals(SourceType.HN_API, adapter.type());
    }

    @Test
    void fetchTopStoriesMapsFields() throws Exception {
        server.enqueue(json("[1,2]"));
        server.enqueue(json("{\"id\":1,\"type\":\"story\",\"title\":\"Hello\",\"by\":\"u1\",\"time\":1750000000,\"url\":\"http://t/1\",\"score\":40}"));
        server.enqueue(json("{\"id\":2,\"type\":\"story\",\"title\":\"World\",\"by\":\"u2\",\"time\":1750000200,\"url\":\"http://t/2\",\"score\":5}"));

        List<FetchedItem> items = adapter.fetch(sourceAtServer(), Instant.parse("2020-01-01T00:00:00Z"));

        assertEquals(2, items.size());
        assertEquals("1", items.get(0).sourceItemId());
        assertEquals("Hello", items.get(0).title());
        assertEquals("http://t/1", items.get(0).url());
        assertEquals("u1", items.get(0).author());
        assertEquals(40, items.get(0).score());
        assertEquals("World", items.get(1).title());
    }

    @Test
    void emptyTopStoriesYieldsNothing() throws Exception {
        server.enqueue(json("[]"));
        assertTrue(adapter.fetch(sourceAtServer(), null).isEmpty());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void skipsDeletedDeadCommentAndUntitledItems() throws Exception {
        server.enqueue(json("[1,2,3,4,5]"));
        server.enqueue(json("{\"id\":1,\"type\":\"comment\",\"title\":\"c\",\"time\":1750000000}"));
        server.enqueue(json("{\"id\":2,\"type\":\"story\",\"title\":\"gone\",\"deleted\":true,\"time\":1750000000}"));
        server.enqueue(json("{\"id\":3,\"type\":\"story\",\"time\":1750000000}"));
        server.enqueue(json("{\"id\":4,\"type\":\"story\",\"title\":\"dead one\",\"dead\":true,\"time\":1750000000}"));
        server.enqueue(json("{\"id\":5,\"type\":\"story\",\"title\":\"keep\",\"url\":\"http://t/5\",\"time\":1750000000}"));

        List<FetchedItem> items = adapter.fetch(sourceAtServer(), null);

        assertEquals(1, items.size());
        assertEquals("5", items.get(0).sourceItemId());
        assertEquals(6, server.getRequestCount());
    }

    @Test
    void sinceSkipsItemsOlderThanTenMinuteGrace() throws Exception {
        long since = 1735689600L;
        server.enqueue(json("[1,2,3]"));
        server.enqueue(json("{\"id\":1,\"type\":\"story\",\"title\":\"new\",\"time\":" + since + "}"));
        server.enqueue(json("{\"id\":2,\"type\":\"story\",\"title\":\"edge\",\"time\":" + (since - 600) + "}"));
        server.enqueue(json("{\"id\":3,\"type\":\"story\",\"title\":\"old\",\"time\":" + (since - 601) + "}"));

        List<FetchedItem> items = adapter.fetch(sourceAtServer(), Instant.ofEpochSecond(since));

        assertEquals(2, items.size());
        assertEquals("new", items.get(0).title());
        assertEquals("edge", items.get(1).title());
    }

    @Test
    void nullSinceKeepsOldItems() throws Exception {
        server.enqueue(json("[1,2]"));
        server.enqueue(json("{\"id\":1,\"type\":\"story\",\"title\":\"old\",\"time\":1625000000}"));
        server.enqueue(json("{\"id\":2,\"type\":\"story\",\"title\":\"older\",\"time\":1610000000}"));

        List<FetchedItem> items = adapter.fetch(sourceAtServer(), null);

        assertEquals(2, items.size());
    }

    @Test
    void fallsBackToItemPageWhenUrlMissing() throws Exception {
        server.enqueue(json("[1]"));
        server.enqueue(json("{\"id\":1,\"type\":\"story\",\"title\":\"No URL\",\"time\":1750000000}"));

        List<FetchedItem> items = adapter.fetch(sourceAtServer(), null);

        assertEquals("https://news.ycombinator.com/item?id=1", items.get(0).url());
    }

    @Test
    void keepsAtMost30Items() throws Exception {
        StringBuilder ids = new StringBuilder("[");
        for (int i = 1; i <= 30; i++) {
            if (i > 1) ids.append(",");
            ids.append(i);
        }
        ids.append("]");
        server.enqueue(json(ids.toString()));
        for (int i = 1; i <= 30; i++) {
            server.enqueue(json("{\"id\":" + i + ",\"type\":\"story\",\"title\":\"t" + i + "\",\"time\":1750000000}"));
        }

        List<FetchedItem> items = adapter.fetch(sourceAtServer(), null);

        assertEquals(30, items.size());
        assertEquals("t30", items.get(29).title());
        assertEquals(31, server.getRequestCount());
    }

    @Test
    void capsIdsAt100() throws Exception {
        StringBuilder ids = new StringBuilder("[");
        for (int i = 1; i <= 101; i++) {
            if (i > 1) ids.append(",");
            ids.append(i);
        }
        ids.append("]");
        server.enqueue(json(ids.toString()));
        for (int i = 1; i <= 100; i++) {
            server.enqueue(json("{\"id\":" + i + ",\"type\":\"comment\",\"time\":1750000000}"));
        }

        List<FetchedItem> items = adapter.fetch(sourceAtServer(), null);

        assertTrue(items.isEmpty());
        assertEquals(101, server.getRequestCount());
    }
}
