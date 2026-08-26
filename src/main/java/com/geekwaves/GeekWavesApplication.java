package com.geekwaves;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.geekwaves.config.CryptoProperties;
import com.geekwaves.config.MonitorProperties;
import com.geekwaves.config.ProxyProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({CryptoProperties.class, MonitorProperties.class, ProxyProperties.class})
public class GeekWavesApplication {
    public static void main(String[] args) {
        SpringApplication.run(GeekWavesApplication.class, args);
    }
}
