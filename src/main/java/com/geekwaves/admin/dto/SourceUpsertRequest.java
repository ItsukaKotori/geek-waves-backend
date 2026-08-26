package com.geekwaves.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SourceUpsertRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String code;
    @NotBlank
    private String type;
    private String baseUrl;
    private String configJson;
    private Boolean enabled;
    private Integer sortOrder;
    private Integer refreshMinutes;
}
