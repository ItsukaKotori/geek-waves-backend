package com.geekwaves.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geekwaves.admin.dto.FrameworkUpsertRequest;
import com.geekwaves.admin.service.AdminConfigService;
import com.geekwaves.aggregation.domain.FrameworkWatch;
import com.geekwaves.aggregation.framework.FrameworkFetchService;
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
@RequestMapping("/api/admin/frameworks")
@RequiredArgsConstructor
public class FrameworkAdminController {
    private final AdminConfigService service;
    private final FrameworkFetchService frameworkFetchService;

    @GetMapping
    public Response<PageResult<FrameworkWatch>> list(PageParam param) {
        Page<FrameworkWatch> page = service.listFrameworks(param.getCurrent(), param.getSize());
        return Response.success(new PageResult<>(page.getRecords(), page.getCurrent(), page.getSize(), page.getTotal()));
    }

    @PostMapping
    public Response<FrameworkWatch> create(@RequestBody @Valid FrameworkUpsertRequest req) {
        return Response.success(service.createFramework(req));
    }

    @PutMapping("/{id}")
    public Response<FrameworkWatch> update(@PathVariable Long id, @RequestBody @Valid FrameworkUpsertRequest req) {
        return Response.success(service.updateFramework(id, req));
    }

    @PostMapping("/{id}/refresh")
    public Response<FrameworkWatch> refresh(@PathVariable Long id) {
        return Response.success(frameworkFetchService.refresh(id));
    }

    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable Long id) {
        service.deleteFramework(id);
        return Response.success();
    }
}
