package com.geekwaves.monitor.support;

import org.springframework.stereotype.Component;

/**
 * 探测目标字段校验:CRUD 与手动探测共用的唯一入口。
 * 目的是防注入/防滥用(host 只允许主机名与 IP 字面量,拒绝 URL 形态),
 * 并把超时/间隔限制在安全区间(防止把站点当作端口扫描跳板)。
 */
@Component
public class ProbeTargetValidator {

    private static final String HOSTNAME_PATTERN = "^[a-zA-Z0-9]([a-zA-Z0-9._-]{0,253}[a-zA-Z0-9])?$";

    /** null 表示通过;非 null 为错误消息 */
    public String validateName(String name) {
        if (name == null || name.isBlank()) return "名称不能为空";
        if (name.length() > 100) return "名称长度不能超过 100";
        return null;
    }

    /** 只允许主机名 / IPv4 / IPv6 字面量;拒绝 URL 前缀、路径、空白与非法字符 */
    public String validateHost(String host) {
        if (host == null || host.isBlank()) return "host 不能为空,只填主机名或 IP(不带协议)";
        String h = host.trim();
        String lower = h.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")
                || lower.startsWith("tcp://") || lower.startsWith("ftp://")) {
            return "只填主机名或 IP,不要带协议前缀";
        }
        if (h.contains("/") || h.contains(" ") || h.contains(";") || h.contains("&") || h.contains("?")) {
            return "host 含非法字符,只填主机名或 IP";
        }
        if (!h.matches(HOSTNAME_PATTERN) && !isIpv6Literal(h)) {
            return "host 不是合法的主机名或 IP";
        }
        return null;
    }

    public String validatePort(Integer port) {
        if (port == null) return "端口不能为空";
        if (port < 1 || port > 65535) return "端口必须在 1-65535 之间";
        return null;
    }

    /** null 走默认值;给定值须在 [200, 5000] ms */
    public String validateTimeoutMs(Integer timeoutMs) {
        if (timeoutMs == null) return null;
        if (timeoutMs < 200 || timeoutMs > 5000) return "超时必须在 200-5000ms 之间";
        return null;
    }

    /** null 走默认值;给定值须在 [10, 86400] s */
    public String validateIntervalSeconds(Integer intervalSeconds) {
        if (intervalSeconds == null) return null;
        if (intervalSeconds < 10 || intervalSeconds > 86400) return "探测间隔必须在 10-86400 秒之间";
        return null;
    }

    private static boolean isIpv6Literal(String h) {
        if (!h.contains(":")) return false;
        // IPv6 字面量:十六进制组与冒号(含 :: 缩写),可带方括号
        String bare = h.startsWith("[") && h.endsWith("]") ? h.substring(1, h.length() - 1) : h;
        return bare.matches("^[0-9a-fA-F:]+$") && bare.length() <= 45;
    }
}
