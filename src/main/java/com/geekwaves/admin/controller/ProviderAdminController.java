package com.geekwaves.admin.controller;

import com.geekwaves.admin.dto.ProviderUpsertRequest;
import com.geekwaves.admin.service.AdminConfigService;
import com.geekwaves.ai.domain.AiProvider;
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
@RequestMapping("/api/admin/providers")
@RequiredArgsConstructor
public class ProviderAdminController {
    private final AdminConfigService service;

    @GetMapping
    public Response<List<AiProvider>> list() {
        return Response.success(service.listProviders());
    }

    @PostMapping
    public Response<AiProvider> create(@RequestBody @Valid ProviderUpsertRequest req) {
        return Response.success(service.createProvider(req));
    }

    @PutMapping("/{id}")
    public Response<AiProvider> update(@PathVariable Long id, @RequestBody @Valid ProviderUpsertRequest req) {
        return Response.success(service.updateProvider(id, req));
    }

    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable Long id) {
        service.deleteProvider(id);
        return Response.success();
    }

    @PostMapping("/{id}/set-default")
    public Response<Void> setDefault(@PathVariable Long id) {
        service.setDefault(id);
        return Response.success();
    }
}
