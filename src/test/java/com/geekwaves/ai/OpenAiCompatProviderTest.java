package com.geekwaves.ai;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiCompatProviderTest {

    private MockWebServer server;
    private OpenAiCompatProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        provider = new OpenAiCompatProvider(WebClient.builder(), JsonMapper.builder().build());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private List<AiChunk> streamSse(String sse, String apiKey) {
        server.enqueue(new MockResponse()
                .setBody(sse)
                .addHeader("Content-Type", "text/event-stream"));
        return provider.streamChat(
                        new AiProviderConfig(server.url("/").toString(), apiKey, "m"),
                        List.of(new AiMessage("user", "hi")))
                .collectList().block();
    }

    @Test
    void streamsDeltasUntilDone() {
        List<AiChunk> chunks = streamSse(
                "data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}\n\n"
                        + "data: {\"choices\":[{\"delta\":{\"content\":\"消息\"}}]}\n\n"
                        + "data: [DONE]\n\n",
                "k");

        assertNotNull(chunks);
        assertEquals(3, chunks.size());
        assertEquals("好消息", chunks.get(0).text() + chunks.get(1).text());
        assertFalse(chunks.get(0).done());
        assertFalse(chunks.get(1).done());
        assertTrue(chunks.get(2).done());
    }

    @Test
    void postsChatCompletionsRequest() throws Exception {
        streamSse("data: [DONE]\n\n", "k");

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/chat/completions", request.getPath());
        var json = JsonMapper.builder().build().readTree(request.getBody().readUtf8());
        assertEquals("m", json.path("model").asString());
        assertEquals(true, json.path("stream").asBoolean());
        assertEquals(1024, json.path("max_tokens").asInt());
        assertEquals("user", json.path("messages").path(0).path("role").asString());
        assertEquals("hi", json.path("messages").path(0).path("content").asString());
    }

    @Test
    void sendsBearerAuthWhenApiKeyPresent() throws Exception {
        streamSse("data: [DONE]\n\n", "s3cret-key");

        assertEquals("Bearer s3cret-key", server.takeRequest().getHeader("Authorization"));
    }

    @Test
    void omitsAuthorizationWhenApiKeyBlank() throws Exception {
        streamSse("data: [DONE]\n\n", "");

        assertNull(server.takeRequest().getHeader("Authorization"));
    }

    @Test
    void filtersMalformedSseLines() {
        List<AiChunk> chunks = streamSse(
                "data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}\n\n"
                        + "data: NOT_A_JSON\n\n"
                        + "data: {\"choices\":[{\"delta\":{\"content\":\"消息\"}}]}\n\n"
                        + "data: [DONE]\n\n",
                "k");

        assertEquals(3, chunks.size());
        assertEquals("好消息", chunks.get(0).text() + chunks.get(1).text());
        assertTrue(chunks.get(2).done());
    }

    @Test
    void trailingEmptyEventsAfterDoneAreIgnored() {
        List<AiChunk> chunks = streamSse(
                "data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}\n\n"
                        + "data: [DONE]\n\n"
                        + "\n\n"
                        + "data: \n\n"
                        + "\n",
                "k");

        assertEquals(2, chunks.size());
        assertEquals("好", chunks.get(0).text());
        assertTrue(chunks.get(1).done());
    }

    @Test
    void serverErrorsPropagateAsStreamFailure() {
        server.enqueue(new MockResponse().setResponseCode(500));
        WebClientResponseException ex = assertThrows(WebClientResponseException.class,
                () -> provider.streamChat(
                                new AiProviderConfig(server.url("/").toString(), "k", "m"),
                                List.of(new AiMessage("user", "hi")))
                        .collectList().block());

        assertEquals(500, ex.getStatusCode().value());
    }
}
