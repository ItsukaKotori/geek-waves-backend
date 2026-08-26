package com.geekwaves.config;

import com.geekwaves.config.domain.ProxyConfig;
import com.geekwaves.config.service.ProxySettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebClientProxyTest {

    @Test
    void sendsConnectToProxyWhenEnabled() throws Exception {
        try (ServerSocket proxy = new ServerSocket(0)) {
            int proxyPort = proxy.getLocalPort();
            AtomicReference<String> firstLine = new AtomicReference<>("");
            Thread proxyThread = new Thread(() -> {
                try (Socket client = proxy.accept()) {
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
                    firstLine.set(in.readLine());
                    OutputStream out = client.getOutputStream();
                    out.write("HTTP/1.1 200 Connection established\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                } catch (Exception ignored) {
                }
            });
            proxyThread.start();

            ProxyConfig cfg = new ProxyConfig();
            cfg.setEnabled(true);
            cfg.setHost("127.0.0.1");
            cfg.setPort(proxyPort);
            ProxySettingsService svc = mock(ProxySettingsService.class);
            when(svc.effective()).thenReturn(cfg);
            when(svc.rawPassword()).thenReturn(null);

            WebClient webClient = new WebClientConfig(svc).webClientBuilder().build();
            // 目标主机需可解析(否则会在代理隧道建立前 DNS 失败);经代理时会发出 CONNECT。
            // 代理在 CONNECT 后不真正转发 TLS,请求最终会失败,但 CONNECT 到达代理即证明代理生效。
            try {
                webClient.get().uri("https://github.com/path").retrieve().bodyToMono(String.class).block();
            } catch (Exception ignored) {
            }

            proxyThread.join(5000);
            assertTrue(firstLine.get().startsWith("CONNECT "),
                    "代理应收到 CONNECT 请求,实际收到: " + firstLine.get());
        }
    }

    @Test
    void buildsWebClientWhenProxyDisabled() {
        ProxyConfig cfg = new ProxyConfig();
        cfg.setEnabled(false);
        cfg.setHost("127.0.0.1");
        cfg.setPort(7890);
        ProxySettingsService svc = mock(ProxySettingsService.class);
        when(svc.effective()).thenReturn(cfg);
        when(svc.rawPassword()).thenReturn(null);
        assertTrue(new WebClientConfig(svc).webClientBuilder().build() != null);
    }
}
