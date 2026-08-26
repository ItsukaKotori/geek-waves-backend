package com.geekwaves.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geekwaves.admin.dto.SourceUpsertRequest;
import com.geekwaves.admin.service.AdminConfigService;
import com.geekwaves.aggregation.domain.InfoSource;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.itsuka.core.domain.Response;
import org.itsuka.web.dto.PageParam;
import org.itsuka.web.dto.PageResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sources")
@RequiredArgsConstructor
public class SourceAdminController {
    private final AdminConfigService service;

    @GetMapping
    public Response<PageResult<InfoSource>> list(PageParam param) {
        Page<InfoSource> page = service.listSources(param.getCurrent(), param.getSize());
        return Response.success(new PageResult<>(page.getRecords(), page.getCurrent(), page.getSize(), page.getTotal()));
    }

    @PostMapping
    public Response<InfoSource> create(@RequestBody @Valid SourceUpsertRequest req) {
        return Response.success(service.createSource(req));
    }

    @PutMapping("/{id}")
    public Response<InfoSource> update(@PathVariable Long id, @RequestBody @Valid SourceUpsertRequest req) {
        return Response.success(service.updateSource(id, req));
    }

    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable Long id) {
        service.deleteSource(id);
        return Response.success();
    }

    @PostMapping("/{id}/trigger-fetch")
    public Response<Boolean> trigger(@PathVariable Long id) {
        return Response.success(service.triggerFetch(id));
    }
}
