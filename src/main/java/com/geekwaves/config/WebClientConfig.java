package com.geekwaves.config;

import com.geekwaves.config.domain.ProxyConfig;
import com.geekwaves.config.service.ProxySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

/**
 * 统一出站 WebClient。所有来源抓取 / AI / 框架 / HTTP 工具都经由该 builder。
 * 代理配置来自设置页(proxy_config 表,启动时回退到 application.yaml),可在界面修改:
 * 通过 proxyWhen(延迟解析)使每次连接按最新 DB 配置决定是否走代理、走哪个代理,无需重启。
 */
@Configuration
@RequiredArgsConstructor
public class WebClientConfig {
    /** 部分 CDN(Cloudflare)会拦截 ReactorNetty 默认 UA,统一伪装常规浏览器 UA */
    static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    private final ProxySettingsService proxySettings;

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .defaultHeader("User-Agent", DEFAULT_USER_AGENT)
                .clientConnector(new ReactorClientHttpConnector(buildHttpClient()));
    }

    /** 延迟解析代理:每次连接读取最新 DB 配置;关闭/未配置时不走代理。
     * 协议启用 H2(H2C 协商 + TLS ALPN 的 H2):部分 CDN(Cloudflare)按 HTTP/2 指纹放行,
     * HTTP/1.1 即使带正常浏览器 UA 也会被 403(linux.do 实测)。 */
    HttpClient buildHttpClient() {
        return HttpClient.create()
                .protocol(HttpProtocol.H2C, HttpProtocol.H2)
                .proxyWhen((config, proxySpec) -> Mono.defer(() -> {
                    ProxyConfig cfg = proxySettings.effective();
                    if (cfg == null || !Boolean.TRUE.equals(cfg.getEnabled())) {
                        return Mono.empty();
                    }
                    ProxyProvider.Builder b = proxySpec.type(ProxyProvider.Proxy.HTTP)
                            .host(cfg.getHost())
                            .port(cfg.getPort());
                    String username = cfg.getUsername();
                    String rawPassword = proxySettings.rawPassword();
                    if (username != null && !username.isBlank()) {
                        b.username(username);
                        if (rawPassword != null) {
                            b.password(s -> rawPassword);
                        }
                    }
                    return Mono.just(b);
                }));
    }
}
