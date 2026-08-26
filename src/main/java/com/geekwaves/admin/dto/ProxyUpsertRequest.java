package com.geekwaves.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProxyUpsertRequest {
    private Boolean enabled;
    @NotBlank
    private String host;
    @Min(1)
    @Max(65535)
    private int port;
    private String username;
    /** 为空表示不修改(编辑时),或清除(若显式传空串) */
    private String password;
}
