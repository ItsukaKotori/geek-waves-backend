package com.geekwaves.monitor.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.itsuka.mybatis.plus.domain.BaseDomain;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("probe_target")
public class ProbeTarget extends BaseDomain {
    private String name;
    private String host;
    private Integer port;
    /** 预留,本期仅 TCP */
    private String protocol;
    private Boolean enabled;
    private Integer timeoutMs;
    private Integer intervalSeconds;
    private Integer sortOrder;
    private LocalDateTime lastProbeAt;
    /** UP / TIMEOUT / DOWN */
    private String lastStatus;
    private Integer lastLatencyMs;
    private String lastError;
    private Integer failCount;
}
