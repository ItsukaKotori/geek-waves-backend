package com.geekwaves.tool.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.Map;

@Data
public class HttpRequestCommand {
    @NotBlank
    @Pattern(regexp = "(?i)GET|POST|PUT|DELETE|PATCH", message = "仅支持 GET/POST/PUT/DELETE/PATCH")
    private String method;
    @NotBlank
    private String url;
    private Map<String, String> headers;
    private String body;
    @Positive
    private Long timeoutMs;
}
