package com.geekwaves.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "geekwaves.monitor")
public class MonitorProperties {
    /** 总开关:false 时停止采样/探测,指标端点返回降级结构 */
    private boolean enabled = true;
    /** 系统指标采样周期(ms) */
    private long sampleMs = 5000;
    /** 进程快照采样周期(ms) */
    private long processSampleMs = 10000;
    /** 进程快照保留条数(按 CPU 降序) */
    private int processTopN = 50;
    /** 端口探测扫描周期(ms) */
    private long probeFixedDelayMs = 30000;
    /** 探测目标数量上限(防端口扫描跳板) */
    private int maxTargets = 64;
}
