package com.geekwaves.admin.controller;

import com.geekwaves.admin.dto.ProxyUpsertRequest;
import com.geekwaves.config.domain.ProxyConfig;
import com.geekwaves.config.service.ProxySettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.itsuka.core.domain.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/proxy")
@RequiredArgsConstructor
public class ProxyAdminController {
    private final ProxySettingsService service;

    @GetMapping
    public Response<ProxyConfig> get() {
        return Response.success(service.current());
    }

    @PutMapping
    public Response<ProxyConfig> update(@RequestBody @Valid ProxyUpsertRequest req) {
        ProxyConfig cfg = service.update(
                req.getEnabled(), req.getHost(), req.getPort(), req.getUsername(), req.getPassword());
        return Response.success(cfg);
    }
}
