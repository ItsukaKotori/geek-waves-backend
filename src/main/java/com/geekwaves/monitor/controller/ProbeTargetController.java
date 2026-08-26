package com.geekwaves.monitor.controller;

import com.geekwaves.monitor.domain.ProbeTarget;
import com.geekwaves.monitor.dto.ProbeTargetUpsertRequest;
import com.geekwaves.monitor.service.PortProbeService;
import com.geekwaves.monitor.service.ProbeTargetAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.itsuka.core.domain.Response;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/monitor/probe-targets")
@RequiredArgsConstructor
public class ProbeTargetController {
    private final ProbeTargetAdminService adminService;
    private final PortProbeService probeService;

    @GetMapping
    public Response<List<ProbeTarget>> list() {
        return Response.success(adminService.list());
    }

    @PostMapping
    public Response<ProbeTarget> create(@RequestBody @Valid ProbeTargetUpsertRequest req) {
        return Response.success(adminService.create(req));
    }

    @PutMapping("/{id}")
    public Response<ProbeTarget> update(@PathVariable Long id, @RequestBody @Valid ProbeTargetUpsertRequest req) {
        return Response.success(adminService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable Long id) {
        adminService.delete(id);
        return Response.success();
    }

    @PostMapping("/{id}/probe")
    public Response<ProbeTarget> probe(@PathVariable Long id) {
        return Response.success(probeService.manualProbe(id));
    }
}
