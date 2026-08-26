package com.geekwaves.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 代理配置(非 DB 场景的兜底默认值)。
 * 若设置页已持久化代理配置(proxy_config 表),以 DB 为准,此值仅作为启动兜底。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "geekwaves.proxy")
public class ProxyProperties {
    private boolean enabled = true;
    private String host = "127.0.0.1";
    private int port = 7890;
    private String username;
    private String password;
}
