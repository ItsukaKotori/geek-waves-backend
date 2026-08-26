package com.geekwaves.monitor.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProbeTargetValidatorTest {

    private final ProbeTargetValidator validator = new ProbeTargetValidator();

    @Test
    void acceptsHostnameIpAndIpv6() {
        assertNull(validator.validateHost("localhost"));
        assertNull(validator.validateHost("127.0.0.1"));
        assertNull(validator.validateHost("192.168.1.10"));
        assertNull(validator.validateHost("db.internal.example.com"));
        assertNull(validator.validateHost("::1"));
        assertNull(validator.validateHost("2001:db8::1"));
    }

    @Test
    void rejectsUrlProtocolPrefixAndBlank() {
        assertTrue(validator.validateHost("http://127.0.0.1").contains("主机名或 IP"));
        assertTrue(validator.validateHost("https://example.com").contains("主机名或 IP"));
        assertTrue(validator.validateHost("127.0.0.1/path").contains("主机名或 IP"));
        assertTrue(validator.validateHost("  ").contains("主机名或 IP"));
        assertTrue(validator.validateHost(null).contains("主机名或 IP"));
    }

    @Test
    void rejectsHostnameWithIllegalCharacters() {
        assertTrue(validator.validateHost("bad host") != null);
        assertTrue(validator.validateHost("host;rm -rf") != null);
        assertTrue(validator.validateHost("a".repeat(256)) != null);
    }

    @Test
    void portMustBeInRange() {
        assertNull(validator.validatePort(1));
        assertNull(validator.validatePort(8082));
        assertNull(validator.validatePort(65535));
        assertTrue(validator.validatePort(null) != null);
        assertTrue(validator.validatePort(0) != null);
        assertTrue(validator.validatePort(65536) != null);
        assertTrue(validator.validatePort(-1) != null);
    }

    @Test
    void timeoutMustBeWithinBounds() {
        assertNull(validator.validateTimeoutMs(null)); // null 走默认值
        assertNull(validator.validateTimeoutMs(200));
        assertNull(validator.validateTimeoutMs(5000));
        assertTrue(validator.validateTimeoutMs(100) != null);
        assertTrue(validator.validateTimeoutMs(6000) != null);
    }

    @Test
    void intervalMustBeWithinBounds() {
        assertNull(validator.validateIntervalSeconds(null)); // null 走默认值
        assertNull(validator.validateIntervalSeconds(10));
        assertNull(validator.validateIntervalSeconds(86400));
        assertTrue(validator.validateIntervalSeconds(5) != null);
        assertTrue(validator.validateIntervalSeconds(90000) != null);
    }

    @Test
    void nameMustBeNonBlankAndBounded() {
        assertNull(validator.validateName("本机后端"));
        assertTrue(validator.validateName(" ") != null);
        assertTrue(validator.validateName("x".repeat(101)) != null);
    }
}
