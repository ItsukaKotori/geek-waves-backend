package com.geekwaves.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CryptoServiceTest {
    private CryptoService service;

    @BeforeEach
    void setUp() {
        CryptoProperties properties = new CryptoProperties();
        properties.setKey(Base64.getEncoder().encodeToString(new byte[32]));
        service = new CryptoService(properties);
    }

    @Test
    void roundTrip() {
        String enc = service.encrypt("sk-abcdefgh12345678");
        assertNotEquals("sk-abcdefgh12345678", enc);
        assertEquals("sk-abcdefgh12345678", service.decrypt(enc));
    }

    @Test
    void encryptedCipherTextDiffersPerCall() {
        assertNotEquals(service.encrypt("same"), service.encrypt("same"));
    }

    @Test
    void missingKeyFailsFastOnConstruction() {
        CryptoProperties properties = new CryptoProperties();
        assertThrows(IllegalStateException.class, () -> new CryptoService(properties));
        assertThrows(IllegalStateException.class, properties::securityKey);
    }

    @Test
    void malformedCipherTextFails() {
        assertThrows(IllegalStateException.class, () -> service.decrypt("aGVsbG8"));
    }
}
