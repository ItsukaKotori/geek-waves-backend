package com.geekwaves.ai;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnthropicProviderTest {

    private MockWebServer server;
    private AnthropicProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        provider = new AnthropicProvider(WebClient.builder(), JsonMapper.builder().build());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private List<AiChunk> streamSse(String sse, String apiKey, List<AiMessage> messages) {
        server.enqueue(new MockResponse()
                .setBody(sse)
                .addHeader("Content-Type", "text/event-stream"));
        return provider.streamChat(
                        new AiProviderConfig(server.url("/").toString(), apiKey, "m"),
                        messages)
                .collectList().block();
    }

    @Test
    void streamsTextDeltasUntilMessageStop() {
        List<AiChunk> chunks = streamSse(
                "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}\n\n"
                        + "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\" there\"}}\n\n"
                        + "data: {\"type\":\"message_stop\"}\n\n",
                "k", List.of(new AiMessage("user", "hi")));

        assertNotNull(chunks);
        assertEquals(3, chunks.size());
        assertEquals("Hi there", chunks.get(0).text() + chunks.get(1).text());
        assertFalse(chunks.get(0).done());
        assertFalse(chunks.get(1).done());
        assertTrue(chunks.get(2).done());
    }

    @Test
    void postsMessagesRequestExtractingSystem() throws Exception {
        streamSse("data: {\"type\":\"message_stop\"}\n\n", "k",
                List.of(new AiMessage("system", "be concise"), new AiMessage("user", "hi")));

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/v1/messages", request.getPath());
        var json = JsonMapper.builder().build().readTree(request.getBody().readUtf8());
        assertEquals("m", json.path("model").asString());
        assertEquals(true, json.path("stream").asBoolean());
        assertEquals(1024, json.path("max_tokens").asInt());
        assertEquals("be concise", json.path("system").asString());
        assertEquals(1, json.path("messages").size());
        assertEquals("user", json.path("messages").path(0).path("role").asString());
        assertEquals("hi", json.path("messages").path(0).path("content").asString());
    }

    @Test
    void sendsXApiKeyAndAnthropicVersionHeaders() throws Exception {
        streamSse("data: {\"type\":\"message_stop\"}\n\n", "s3cret-key",
                List.of(new AiMessage("user", "hi")));

        RecordedRequest request = server.takeRequest();
        assertEquals("s3cret-key", request.getHeader("x-api-key"));
        assertEquals("2023-06-01", request.getHeader("anthropic-version"));
    }

    @Test
    void sendsXApiKeyEvenWhenBlank() throws Exception {
        streamSse("data: {\"type\":\"message_stop\"}\n\n", "",
                List.of(new AiMessage("user", "hi")));

        assertEquals("", server.takeRequest().getHeader("x-api-key"));
    }

    @Test
    void ignoresToolUseDeltasAndMalformedLines() {
        List<AiChunk> chunks = streamSse(
                "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}\n\n"
                        + "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{}\"}}\n\n"
                        + "data: NOT_A_JSON\n\n"
                        + "data: {\"type\":\"message_stop\"}\n\n",
                "k", List.of(new AiMessage("user", "hi")));

        assertEquals(2, chunks.size());
        assertEquals("Hi", chunks.get(0).text());
        assertTrue(chunks.get(1).done());
    }

    @Test
    void serverErrorBecomesDoneChunkWithInterruptionMessage() {
        server.enqueue(new MockResponse().setResponseCode(500));
        List<AiChunk> chunks = provider.streamChat(
                        new AiProviderConfig(server.url("/").toString(), "k", "m"),
                        List.of(new AiMessage("user", "hi")))
                .collectList().block();

        assertNotNull(chunks);
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).done());
        assertTrue(chunks.get(0).text().contains("[流式中断]"));
    }
}
