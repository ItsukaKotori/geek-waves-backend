package com.geekwaves.tool.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.itsuka.core.exception.ServiceException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SsrFGuardTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/x", "http://localhost:8080/", "http://10.0.0.5/api",
            "http://192.168.1.1:9000/", "http://172.16.0.1/", "http://169.254.169.254/",
            "file:///etc/passwd", "ftp://example.com/", "http://[::1]/",
            "http://0.0.0.0:8080/", "http://xxx.internal/", "http://db.local/",
            "http://[fc00::1]/", "http://[fd00::1]/",
            "http://2130706433/", "http://0x7F000001/"
    })
    void blocksPrivateOrUnsafe(String url) {
        assertThrows(ServiceException.class, () -> SsrFGuard.assertSafe(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://api.github.com/repos", "http://example.com:8080/ok",
            "https://news.ycombinator.com/"
    })
    void allowsPublic(String url) {
        assertDoesNotThrow(() -> SsrFGuard.assertSafe(url));
    }
}
