package com.geekwaves.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AiConfig {

    @Bean
    public AiProviderRegistry aiProviderRegistry(List<AiProvider> providers) {
        return AiProviderRegistry.of(providers);
    }
}
