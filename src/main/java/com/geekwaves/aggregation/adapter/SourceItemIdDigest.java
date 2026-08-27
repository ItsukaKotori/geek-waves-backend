package com.geekwaves.aggregation.adapter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class SourceItemIdDigest {
    public static final int HEX_LEN = 40;

    private SourceItemIdDigest() {
    }

    /** SHA-256 摘要前 40 个十六进制字符:适配器生成与 NewsService 截断共用的统一规则 */
    public static String sha256First40(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8))).substring(0, HEX_LEN);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
