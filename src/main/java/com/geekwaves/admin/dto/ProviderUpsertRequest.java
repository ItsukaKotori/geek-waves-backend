package com.geekwaves.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProviderUpsertRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String vendor;    // OPENAI_COMPAT | ANTHROPIC
    private String baseUrl;
    private String apiKey;              // 明文 key,仅写入时使用
    private String model;
    private Boolean enabled;
    private Boolean isDefault;
}
