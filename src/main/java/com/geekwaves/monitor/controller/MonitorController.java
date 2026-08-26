package com.geekwaves.monitor.controller;

import com.geekwaves.config.MonitorProperties;
import com.geekwaves.monitor.dto.OverviewDto;
import com.geekwaves.monitor.dto.ProcessSnapshotDto;
import com.geekwaves.monitor.dto.SystemMetricsDto;
import com.geekwaves.monitor.dto.TaskStatusDto;
import com.geekwaves.monitor.service.JvmMetricsService;
import com.geekwaves.monitor.service.ProcessMetricsSampler;
import com.geekwaves.monitor.service.SystemMetricsSampler;
import com.geekwaves.monitor.service.TaskStatusService;
import lombok.RequiredArgsConstructor;
import org.itsuka.core.domain.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
public class MonitorController {
    private final SystemMetricsSampler systemSampler;
    private final ProcessMetricsSampler processSampler;
    private final JvmMetricsService jvmMetricsService;
    private final TaskStatusService taskStatusService;
    private final MonitorProperties props;

    @GetMapping("/overview")
    public Response<OverviewDto> overview() {
        return Response.success(new OverviewDto(systemOrDegraded(), jvmMetricsService.metrics()));
    }

    @GetMapping("/processes")
    public Response<ProcessSnapshotDto> processes() {
        if (!props.isEnabled()) {
            return Response.success(ProcessSnapshotDto.degraded(now(), "监控已禁用"));
        }
        ProcessSnapshotDto s = processSampler.latest();
        return Response.success(s != null ? s : ProcessSnapshotDto.degraded(now(), "采样中"));
    }

    @GetMapping("/tasks")
    public Response<TaskStatusDto> tasks() {
        return Response.success(taskStatusService.status());
    }

    private SystemMetricsDto systemOrDegraded() {
        if (!props.isEnabled()) {
            return SystemMetricsDto.degraded(now(), "监控已禁用");
        }
        SystemMetricsDto s = systemSampler.latest();
        return s != null ? s : SystemMetricsDto.degraded(now(), "采样中");
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}
