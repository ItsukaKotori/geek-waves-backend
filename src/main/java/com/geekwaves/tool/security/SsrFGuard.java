package com.geekwaves.tool.security;

import org.itsuka.core.exception.ServiceException;
import org.springframework.http.HttpStatus;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Set;

public final class SsrFGuard {
    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost", "", "0.0.0.0", "127.0.0.1", "::1");
    private static final Set<String> BLOCKED_SUFFIXES = Set.of(
            ".local", ".internal", ".lan", ".localhost");

    private SsrFGuard() {}

    public static void assertSafe(String rawUrl) {
        URI uri;
        try {
            uri = new URI(rawUrl);
        } catch (Exception e) {
            throw ServiceException.create(HttpStatus.BAD_REQUEST, "非法 URL");
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw ServiceException.create(HttpStatus.BAD_REQUEST, "仅支持 http/https");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw ServiceException.create(HttpStatus.BAD_REQUEST, "目标地址不在允许范围");
        }
        host = host.toLowerCase();
        if (BLOCKED_HOSTS.contains(host)) {
            throw ServiceException.create(HttpStatus.BAD_REQUEST, "目标地址不在允许范围");
        }
        if (BLOCKED_SUFFIXES.stream().anyMatch(host::endsWith)) {
            throw ServiceException.create(HttpStatus.BAD_REQUEST, "目标地址不在允许范围");
        }
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (isPrivate(addr)) {
                    throw ServiceException.create(HttpStatus.BAD_REQUEST, "目标地址不在允许范围");
                }
            }
        } catch (UnknownHostException e) {
            throw ServiceException.create(HttpStatus.BAD_REQUEST, "域名无法解析");
        }
    }

    private static boolean isPrivate(InetAddress addr) {
        if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()
                || addr.isAnyLocalAddress() || addr.isMulticastAddress()) {
            return true;
        }
        if (addr instanceof Inet6Address inet6) {
            if (isIpv4Mapped(inet6)) {
                return isPrivate(toIpv4(inet6));
            }
            byte[] b = inet6.getAddress();
            if ((b[0] & 0xFE) == 0xFC) {
                return true;
            }
            return false;
        }
        if (addr instanceof Inet4Address) {
            byte[] b = addr.getAddress();
            int first = b[0] & 0xFF;
            if (first == 10 || first == 127) return true;
            if (first == 172 && (b[1] & 0xF0) == 16) return true;
            if (first == 192 && b[1] == 168) return true;
            if (first == 169 && b[1] == 254) return true;
            if (first == 100 && (b[1] & 0xC0) == 64) return true;
            if (first == 0) return true;
        }
        return false;
    }

    private static boolean isIpv4Mapped(Inet6Address addr) {
        byte[] b = addr.getAddress();
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) return false;
        }
        return b[10] == (byte) 0xFF && b[11] == (byte) 0xFF;
    }

    private static Inet4Address toIpv4(InetAddress addr) {
        try {
            byte[] bytes = addr.getAddress();
            if (bytes.length != 16) return (Inet4Address) addr;
            byte[] v4 = new byte[4];
            System.arraycopy(bytes, 12, v4, 0, 4);
            return (Inet4Address) InetAddress.getByAddress(v4);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
