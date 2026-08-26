package com.geekwaves.aggregation.config;

import com.geekwaves.aggregation.adapter.AdapterRegistry;
import com.geekwaves.aggregation.adapter.SourceAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AggregationConfig {

    @Bean
    public AdapterRegistry adapterRegistry(List<SourceAdapter> adapters) {
        return AdapterRegistry.of(adapters);
    }
}
