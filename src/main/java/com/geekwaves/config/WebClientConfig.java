package com.geekwaves.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024));
    }
}
