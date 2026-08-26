package com.geekwaves.tool.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class HttpRequestResult {
    private int status;
    private long tookMs;
    private Map<String, String> headers;
    private String body;
}
