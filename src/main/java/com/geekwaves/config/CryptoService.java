package com.geekwaves.config;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class CryptoService {
    private static final int GCM_BITS = 128;
    private static final int IV_LENGTH = 12;
    private final CryptoProperties properties;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(CryptoProperties properties) {
        this.properties = properties;
        validateKey();
    }

    @PostConstruct
    void validateKey() {
        properties.securityKey();
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, properties.securityKey(), new GCMParameterSpec(GCM_BITS, iv));
            byte[] out = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] all = new byte[iv.length + out.length];
            System.arraycopy(iv, 0, all, 0, 12);
            System.arraycopy(out, 0, all, 12, out.length);
            return Base64.getEncoder().encodeToString(all);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            if (all == null || all.length < IV_LENGTH + 1) {
                throw new IllegalStateException("密文格式错误");
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(all, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, properties.securityKey(), new GCMParameterSpec(GCM_BITS, iv));
            byte[] out = cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH);
            return new String(out, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("解密失败", e);
        }
    }
}
