package com.geekwaves.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Getter
@Setter
@ConfigurationProperties(prefix = "geekwaves.crypto")
public class CryptoProperties {
    private String key;

    public SecretKey securityKey() {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("GEEKWAVES_CRYPTO_KEY 未配置(环境变量),拒绝启动");
        }
        byte[] raw = Base64.getDecoder().decode(key.getBytes(StandardCharsets.UTF_8));
        if (raw.length != 32) {
            throw new IllegalStateException("GEEKWAVES_CRYPTO_KEY 必须为 32 字节(256bit) base64");
        }
        return new SecretKeySpec(raw, "AES");
    }
}
