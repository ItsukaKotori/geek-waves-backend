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

class JsonApiAdapterTest {

    private MockWebServer server;
    private JsonApiAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        adapter = new JsonApiAdapter(WebClient.builder(), new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private InfoSource sourceAtServer() {
        InfoSource source = new InfoSource();
        source.setBaseUrl(server.url("/api/list").toString());
        return source;
    }

    private static MockResponse json(String body) {
        return new MockResponse().setBody(body).addHeader("Content-Type", "application/json");
    }

    private static final String V2EX_LIKE = """
            [{"id":123,"title":"x","url":"/u/1","created_time":1750000000000,
              "content":"c","member":{"username":"u1"},"votes":10}]""";

    @Test
    void typeIsJsonApi() {
        assertEquals(SourceType.JSON_API, adapter.type());
    }

    @Test
    void fetchMapsFieldsWithDefaultConfig() throws Exception {
        server.enqueue(json(V2EX_LIKE));

        List<FetchedItem> items = adapter.fetch(sourceAtServer(), null);

        assertEquals(1, items.size());
        FetchedItem item = items.get(0);
        assertEquals("123", item.sourceItemId());
        assertEquals("x", item.title());
        assertEquals("/u/1", item.url());
        assertEquals("c", item.summary());
        assertEquals("u1", item.author());
        assertEquals(10, item.score());
        assertEquals(LocalDateTime.ofInstant(Instant.ofEpochMilli(1750000000000L), ZoneId.systemDefault()), item.publishedAt());
    }

    @Test
    void secondsTimestampsAlsoSupported() throws Exception {
        server.enqueue(json("[{\"id\":1,\"title\":\"s\",\"created_time\":1750000000}]"));

        List<FetchedItem> items = adapter.fetch(sourceAtServer(), null);

        assertEquals(LocalDateTime.ofInstant(Instant.ofEpochSecond(1750000000L), ZoneId.systemDefault()), items.get(0).publishedAt());
    }

    @Test
    void configOverridesAllPaths() throws Exception {
        server.enqueue(json("""
                {"name":"T","link":"http://t/1","ts":1750000000000,"desc":"d",
                 "user":"alice","uid":"abc","score":5}"""));
        InfoSource source = sourceAtServer();
        source.setConfigJson("""
                {"itemsPath":"$","titlePath":"$.name","urlPath":"$.link","timePath":"$.ts",
                 "summaryPath":"$.desc","authorPath":"$.user","idPath":"$.uid","scorePath":"$.score"}""");

        List<FetchedItem> items = adapter.fetch(source, null);

        assertEquals(1, items.size());
        FetchedItem item = items.get(0);
        assertEquals("abc", item.sourceItemId());
        assertEquals("T", item.title());
        assertEquals("http://t/1", item.url());
        assertEquals("d", item.summary());
        assertEquals("alice", item.author());
        assertEquals(5, item.score());
    }

    @Test
    void invalidTimeSkipsEntryWithoutBreakingFetch() throws Exception {
        server.enqueue(json("""
                [{"id":1,"title":"bad","url":"/u/2","created_time":"nope"},
                 {"id":2,"title":"good","url":"/u/3","created_time":1750000000000}]"""));

        List<FetchedItem> items = adapter.fetch(sourceAtServer(), null);

        assertEquals(1, items.size());
        assertEquals("good", items.get(0).title());
    }

    @Test
    void missingPathYieldsEmptyField() throws Exception {
        server.enqueue(json(V2EX_LIKE));
        InfoSource source = sourceAtServer();
        source.setConfigJson("{\"idPath\":\"$.missing\",\"urlPath\":\"$.missing\"}");

        List<FetchedItem> items = adapter.fetch(source, null);

        assertEquals(1, items.size());
        assertEquals("", items.get(0).sourceItemId());
        assertEquals("", items.get(0).url());
    }

    @Test
    void sinceSkipsItemsOlderThanTenMinuteGrace() throws Exception {
        long sinceMs = 1750000000000L;
        server.enqueue(json("""
                [{"id":1,"title":"new","created_time":%d},
                 {"id":2,"title":"edge","created_time":%d},
                 {"id":3,"title":"old","created_time":%d}]""".formatted(sinceMs, sinceMs - 600_000, sinceMs - 601_000)));

        List<FetchedItem> items = adapter.fetch(sourceAtServer(), Instant.ofEpochMilli(sinceMs));

        assertEquals(2, items.size());
        assertEquals("new", items.get(0).title());
        assertEquals("edge", items.get(1).title());
    }

    @Test
    void scorePathMissingDefaultsZero() throws Exception {
        server.enqueue(json("[{\"id\":1,\"title\":\"s\",\"created_time\":1750000000000}]"));
        InfoSource source = sourceAtServer();
        source.setConfigJson("{\"scorePath\":\"$.votes\"}");

        List<FetchedItem> items = adapter.fetch(source, null);

        assertEquals(0, items.get(0).score());
    }
}
