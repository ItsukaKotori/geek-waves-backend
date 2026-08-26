package com.geekwaves.monitor.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 探测目标创建/更新请求。数值边界由 ProbeTargetValidator 统一校验
 * (host 格式/端口区间/超时区间/间隔区间),此处只做非空兜底。
 */
public record ProbeTargetUpsertRequest(
        @NotBlank(message = "名称不能为空") String name,
        @NotBlank(message = "host 不能为空") String host,
        Integer port,
        Boolean enabled,
        Integer timeoutMs,
        Integer intervalSeconds,
        Integer sortOrder) {
}
