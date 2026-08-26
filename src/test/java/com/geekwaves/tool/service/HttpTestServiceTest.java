package com.geekwaves.tool.service;

import com.geekwaves.tool.dto.HttpRequestCommand;
import com.geekwaves.tool.dto.HttpRequestResult;
import com.geekwaves.tool.security.SsrFGuard;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class HttpTestServiceTest {
    private MockWebServer server;
    private HttpTestService service;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        service = new HttpTestService(WebClient.builder());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void getReturnsStatusTookMsAndBody() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("hello")
                .addHeader("Content-Type", "text/plain"));

        HttpRequestCommand cmd = new HttpRequestCommand();
        cmd.setMethod("GET");
        cmd.setUrl(server.url("/ping").toString());
        cmd.setTimeoutMs(5000L);

        HttpRequestResult result;
        try (MockedStatic<SsrFGuard> guard = mockStatic(SsrFGuard.class)) {
            result = service.send(cmd).block();
        }

        assertEquals(200, result.getStatus());
        assertEquals("hello", result.getBody());
        assertTrue(result.getTookMs() >= 0);
        assertEquals("text/plain", result.getHeaders().get("Content-Type"));
    }

    @Test
    void postSendsBodyAndGetsStatusCode() {
        server.enqueue(new MockResponse().setResponseCode(204));

        HttpRequestCommand cmd = new HttpRequestCommand();
        cmd.setMethod("POST");
        cmd.setUrl(server.url("/submit").toString());
        cmd.setBody("{\"q\":\"spring\"}");
        cmd.setTimeoutMs(5000L);

        HttpRequestResult result;
        try (MockedStatic<SsrFGuard> guard = mockStatic(SsrFGuard.class)) {
            result = service.send(cmd).block();
        }

        assertEquals(204, result.getStatus());
        assertEquals("", result.getBody());
    }

    @Test
    void redirectNotFollowed() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setResponseCode(302)
                .addHeader("Location", "https://example.com/target")
                .setBody("redirecting"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("landed"));

        HttpRequestCommand cmd = new HttpRequestCommand();
        cmd.setMethod("GET");
        cmd.setUrl(server.url("/redir").toString());
        cmd.setTimeoutMs(5000L);

        HttpRequestResult result;
        try (MockedStatic<SsrFGuard> guard = mockStatic(SsrFGuard.class)) {
            result = service.send(cmd).block();
        }

        assertEquals(302, result.getStatus());
        assertEquals(1, server.getRequestCount());
    }
}
