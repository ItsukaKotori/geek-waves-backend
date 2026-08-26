package com.geekwaves.config;

import com.geekwaves.config.port.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
@Configuration
public class StoragePortConfig {
    @Bean
    @ConditionalOnProperty(name = "geekwaves.storage.cache", havingValue = "local", matchIfMissing = true)
    public CachePort localCachePort(ObjectMapper objectMapper) {
        return new LocalCachePort(objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "geekwaves.storage.cache", havingValue = "local", matchIfMissing = true)
    public LockPort localLockPort() {
        return new LocalLockPort();
    }

    @Bean
    @ConditionalOnProperty(name = "geekwaves.storage.cache", havingValue = "local", matchIfMissing = true)
    public RateLimitPort localRateLimitPort() {
        return new LocalRateLimitPort();
    }
}
